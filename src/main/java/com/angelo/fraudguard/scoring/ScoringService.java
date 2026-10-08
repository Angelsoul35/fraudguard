package com.angelo.fraudguard.scoring;

import com.angelo.fraudguard.domain.Assessment;
import com.angelo.fraudguard.domain.Decision;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.events.EventBus;
import com.angelo.fraudguard.events.TransactionReceived;
import com.angelo.fraudguard.rules.RuleContext;
import com.angelo.fraudguard.rules.RuleEngine;
import com.angelo.fraudguard.velocity.VelocitySnapshot;
import com.angelo.fraudguard.velocity.VelocityStore;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The scoring subscriber: the consumer end of the event-driven pipeline.
 *
 * <p>On startup it subscribes to {@link TransactionReceived}. For each event it
 * runs one atomic per-card step — read the velocity snapshot and record the
 * transaction ({@link VelocityStore#observe}) — then evaluates the rule engine
 * against that snapshot, aggregates the score, derives the decision and stores
 * the {@link Assessment}. Because {@code observe} serializes per card, the whole
 * read-evaluate-record sequence is consistent for a given card even though
 * different cards are scored concurrently on their own virtual threads.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final EventBus eventBus;
    private final VelocityStore velocityStore;
    private final RuleEngine ruleEngine;
    private final RiskScorer riskScorer;
    private final AssessmentStore assessmentStore;

    public ScoringService(EventBus eventBus,
                          VelocityStore velocityStore,
                          RuleEngine ruleEngine,
                          RiskScorer riskScorer,
                          AssessmentStore assessmentStore) {
        this.eventBus = eventBus;
        this.velocityStore = velocityStore;
        this.ruleEngine = ruleEngine;
        this.riskScorer = riskScorer;
        this.assessmentStore = assessmentStore;
    }

    @PostConstruct
    void subscribe() {
        eventBus.subscribe(TransactionReceived.class, event -> score(event.transaction()));
    }

    /**
     * Score a single transaction end to end and store the assessment. Invoked by
     * the subscriber for each event; also callable directly for synchronous
     * unit tests of the pipeline.
     *
     * @return the stored assessment
     */
    public Assessment score(Transaction transaction) {
        VelocitySnapshot snapshot = velocityStore.observe(transaction);
        List<RuleOutcome> outcomes = ruleEngine.evaluate(new RuleContext(transaction, snapshot));
        List<RuleOutcome> fired = outcomes.stream().filter(RuleOutcome::fired).toList();

        int score = riskScorer.score(outcomes);
        Decision decision = riskScorer.decide(score);

        Assessment assessment = new Assessment(
                transaction.id(), transaction.cardId(), score, decision, fired, Instant.now());
        assessmentStore.save(assessment);

        log.info("Scored tx {} card {}: score={} decision={} firedRules={}",
                transaction.id(), transaction.cardId(), score, decision,
                fired.stream().map(RuleOutcome::ruleId).toList());
        return assessment;
    }
}
