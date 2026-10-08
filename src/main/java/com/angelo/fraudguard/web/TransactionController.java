package com.angelo.fraudguard.web;

import com.angelo.fraudguard.domain.Assessment;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.events.EventBus;
import com.angelo.fraudguard.events.TransactionReceived;
import com.angelo.fraudguard.exception.AssessmentNotFoundException;
import com.angelo.fraudguard.scoring.AssessmentStore;
import com.angelo.fraudguard.web.dto.IngestResponse;
import com.angelo.fraudguard.web.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP surface of the service.
 *
 * <ul>
 *   <li>{@code POST /transactions} ingests a transaction and publishes a
 *       {@link TransactionReceived} event to the bus, returning {@code 202
 *       Accepted} immediately — it never blocks on scoring.</li>
 *   <li>{@code GET /transactions/{id}/assessment} returns the assessment the
 *       scoring subscriber produced, or {@code 404} if it is unknown or not yet
 *       scored.</li>
 * </ul>
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final EventBus eventBus;
    private final AssessmentStore assessmentStore;

    public TransactionController(EventBus eventBus, AssessmentStore assessmentStore) {
        this.eventBus = eventBus;
        this.assessmentStore = assessmentStore;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<IngestResponse> ingest(@RequestBody TransactionRequest request) {
        Transaction transaction = request.toTransaction();
        eventBus.publish(TransactionReceived.of(transaction));
        log.debug("Accepted tx {} for card {}", transaction.id(), transaction.cardId());
        return ResponseEntity.accepted().body(IngestResponse.accepted(transaction.id()));
    }

    @GetMapping("/{id}/assessment")
    public Assessment assessment(@PathVariable String id) {
        return assessmentStore.find(id)
                .orElseThrow(() -> new AssessmentNotFoundException(id));
    }
}
