package com.angelo.fraudguard.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.angelo.fraudguard.domain.Assessment;
import com.angelo.fraudguard.domain.Decision;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.events.EventBus;
import com.angelo.fraudguard.events.TransactionReceived;
import com.angelo.fraudguard.rules.AmountVelocityRule;
import com.angelo.fraudguard.rules.ImpossibleTravelRule;
import com.angelo.fraudguard.rules.NewCountryRule;
import com.angelo.fraudguard.rules.RoundAmountHighValueRule;
import com.angelo.fraudguard.rules.TransactionFrequencyRule;
import com.angelo.fraudguard.velocity.VelocityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end tests that go through the real in-memory event bus, so they also
 * prove the bus delivers events asynchronously to the scoring subscriber. Each
 * assessment is awaited deterministically with Awaitility rather than slept on.
 */
@SpringBootTest
class ScoringPipelineTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private AssessmentStore assessmentStore;

    @Autowired
    private VelocityStore velocityStore;

    private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");

    @BeforeEach
    void reset() {
        assessmentStore.clear();
        velocityStore.clear();
    }

    private static Transaction tx(String id, String card, long amount, String country, Instant ts) {
        return new Transaction(id, card, amount, "USD", "Shop", country, ts);
    }

    /** Publish to the bus and wait until the subscriber has produced the assessment. */
    private Assessment publishAndAwait(Transaction transaction) {
        eventBus.publish(TransactionReceived.of(transaction));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> assessmentStore.find(transaction.id()).isPresent());
        return assessmentStore.find(transaction.id()).orElseThrow();
    }

    @Test
    void bus_delivers_and_a_normal_transaction_is_approved() {
        Assessment assessment = publishAndAwait(tx("ok-1", "card-normal", 4_599, "US", T0));

        assertThat(assessment.decision()).isEqualTo(Decision.APPROVE);
        assertThat(assessment.score()).isZero();
        assertThat(assessment.firedRules()).isEmpty();
    }

    @Test
    void a_fraud_sequence_escalates_to_decline_with_the_expected_rules() {
        // Six ordinary US charges one minute apart build up frequency on the card.
        for (int i = 0; i < 6; i++) {
            publishAndAwait(tx("burst-" + i, "card-fraud", 2_000, "US", T0.plus(Duration.ofMinutes(i))));
        }
        // Then a seventh charge in Japan one minute later: a burst (frequency), a new
        // country, and impossible travel from the US all at once.
        Assessment assessment =
                publishAndAwait(tx("burst-final", "card-fraud", 2_000, "JP", T0.plus(Duration.ofMinutes(7))));

        assertThat(assessment.decision()).isEqualTo(Decision.DECLINE);
        assertThat(assessment.score()).isGreaterThanOrEqualTo(70);

        List<String> firedIds = assessment.firedRules().stream().map(RuleOutcome::ruleId).toList();
        assertThat(firedIds).contains(
                TransactionFrequencyRule.ID,
                NewCountryRule.ID,
                ImpossibleTravelRule.ID);
        // Every fired rule carries a human-readable reason.
        assertThat(assessment.firedRules()).allSatisfy(r -> assertThat(r.reason()).isNotBlank());
    }

    @Test
    void a_single_weak_signal_does_not_escalate() {
        // Establish a US baseline, then one ordinary charge in France 20 hours later:
        // only the new-country rule fires (weight 20), which is below the REVIEW
        // threshold (40), so a lone weak signal stays APPROVE.
        publishAndAwait(tx("base", "card-travel", 3_000, "US", T0));
        Assessment assessment =
                publishAndAwait(tx("fr", "card-travel", 3_000, "FR", T0.plus(Duration.ofHours(20))));

        assertThat(assessment.score()).isEqualTo(20);
        assertThat(assessment.decision()).isEqualTo(Decision.APPROVE);
        assertThat(assessment.firedRules()).extracting(RuleOutcome::ruleId).containsExactly(NewCountryRule.ID);
    }

    @Test
    void two_signals_combine_into_review() {
        // Four small US charges set a ~20.00 average, then a round 1000.00 US charge:
        // amount-spike (30) + round-high-value (15) = 45 -> REVIEW, no location rules.
        for (int i = 0; i < 4; i++) {
            publishAndAwait(tx("small-" + i, "card-spend", 2_000, "US", T0.plus(Duration.ofMinutes(i))));
        }
        Assessment assessment =
                publishAndAwait(tx("big", "card-spend", 100_000, "US", T0.plus(Duration.ofMinutes(4))));

        assertThat(assessment.decision()).isEqualTo(Decision.REVIEW);
        assertThat(assessment.score()).isBetween(40, 69);
        assertThat(assessment.firedRules()).extracting(RuleOutcome::ruleId)
                .contains(AmountVelocityRule.ID, RoundAmountHighValueRule.ID);
    }
}
