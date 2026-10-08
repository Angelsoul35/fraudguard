package com.angelo.fraudguard.web;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.angelo.fraudguard.scoring.AssessmentStore;
import com.angelo.fraudguard.velocity.VelocityStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives the service through its HTTP surface with MockMvc: ingestion returns
 * 202, the asynchronous assessment is awaited and read back, and the error
 * paths (validation, unknown id) return the right status.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AssessmentStore assessmentStore;

    @Autowired
    private VelocityStore velocityStore;

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @BeforeEach
    void reset() {
        assessmentStore.clear();
        velocityStore.clear();
    }

    private String body(String id, String card, long amount, String country, Instant ts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("cardId", card);
        m.put("amount", amount);
        m.put("currency", "USD");
        m.put("merchant", "Example Store");
        m.put("country", country);
        m.put("timestamp", ts.toString());
        try {
            return json.writeValueAsString(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void ingestAndAwait(String id, String card, long amount, String country, Instant ts) throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, card, amount, country, ts)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.assessmentUrl").value("/transactions/" + id + "/assessment"));
        await().atMost(Duration.ofSeconds(5)).until(() -> assessmentStore.find(id).isPresent());
    }

    @Test
    void normal_transaction_is_accepted_and_assessed_as_approve() throws Exception {
        ingestAndAwait("tx-ok", "card-a", 4_599, "US", T0);

        mvc.perform(get("/transactions/{id}/assessment", "tx-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.firedRules").isEmpty());
    }

    @Test
    void fraud_sequence_is_assessed_as_decline_with_fired_rules() throws Exception {
        for (int i = 0; i < 6; i++) {
            ingestAndAwait("f-" + i, "card-b", 2_000, "US", T0.plus(Duration.ofMinutes(i)));
        }
        ingestAndAwait("f-final", "card-b", 2_000, "JP", T0.plus(Duration.ofMinutes(7)));

        mvc.perform(get("/transactions/{id}/assessment", "f-final"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINE"))
                .andExpect(jsonPath("$.score", greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$.firedRules[*].ruleId",
                        hasItems("VELOCITY_FREQUENCY", "NEW_COUNTRY", "IMPOSSIBLE_TRAVEL")));
    }

    @Test
    void invalid_request_is_rejected_with_400_and_field_errors() throws Exception {
        // Missing cardId, bad currency, negative amount.
        String bad = "{\"id\":\"x\",\"amount\":-5,\"currency\":\"US\",\"merchant\":\"M\",\"country\":\"US\"}";
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.details", containsInAnyOrder(
                        "cardId is required",
                        "amount must be >= 0 (integer cents)",
                        "currency must be a 3-letter ISO 4217 code")));
    }

    @Test
    void malformed_json_is_rejected_with_400() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));
    }

    @Test
    void unknown_assessment_returns_404() throws Exception {
        mvc.perform(get("/transactions/{id}/assessment", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
