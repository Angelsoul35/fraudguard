package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.rules.AmountVelocityRule;
import com.angelo.fraudguard.rules.ImpossibleTravelRule;
import com.angelo.fraudguard.rules.NewCountryRule;
import com.angelo.fraudguard.rules.RoundAmountHighValueRule;
import com.angelo.fraudguard.rules.Rule;
import com.angelo.fraudguard.rules.RuleContext;
import com.angelo.fraudguard.rules.RuleEngine;
import com.angelo.fraudguard.rules.TransactionFrequencyRule;
import com.angelo.fraudguard.scoring.RiskScorer;
import com.angelo.fraudguard.velocity.InMemoryVelocityStore;
import com.angelo.fraudguard.velocity.VelocitySnapshot;
import java.util.List;

/**
 * Wires FraudGuard's production scoring components together outside Spring so the
 * benchmark can run the real model over a dataset.
 *
 * <p>It constructs the exact production objects — the five {@link Rule}
 * implementations, the {@link RuleEngine} that aggregates them, the
 * {@link InMemoryVelocityStore} that builds per-card sliding-window features, and
 * the {@link RiskScorer} that turns rule outcomes into a bounded score — and runs
 * them in the same order as
 * {@link com.angelo.fraudguard.scoring.ScoringService#score}: observe the
 * velocity snapshot (which also records the transaction), evaluate every rule
 * against it, then aggregate. There is no reimplementation of scoring here; this
 * class only omits the asynchronous event bus and the assessment store, which do
 * not affect the score. {@code ScoringHarnessEquivalenceTest} pins the harness
 * output to {@code ScoringService}'s so the two cannot drift.
 *
 * <p>Instances are single-threaded and stateful: the velocity store accumulates
 * history, so a fresh harness must be used for each independent split and
 * transactions must be fed in ascending event-time order.
 */
public final class ScoringHarness {

    private final InMemoryVelocityStore velocityStore;
    private final RuleEngine ruleEngine;
    private final RiskScorer riskScorer;

    public ScoringHarness(ScoringProperties properties) {
        this.velocityStore = new InMemoryVelocityStore(properties);
        List<Rule> rules = List.of(
                new AmountVelocityRule(properties),
                new TransactionFrequencyRule(properties),
                new ImpossibleTravelRule(properties),
                new RoundAmountHighValueRule(properties),
                new NewCountryRule(properties));
        this.ruleEngine = new RuleEngine(rules);
        this.riskScorer = new RiskScorer(properties);
    }

    /**
     * Score a single transaction, mutating the velocity state exactly as the
     * production pipeline does. Returns the aggregated risk score in {@code [0,100]}.
     */
    public int score(Transaction transaction) {
        VelocitySnapshot snapshot = velocityStore.observe(transaction);
        List<RuleOutcome> outcomes = ruleEngine.evaluate(new RuleContext(transaction, snapshot));
        return riskScorer.score(outcomes);
    }

    /**
     * Score a time-ordered batch of samples, returning the per-sample scores as
     * doubles aligned to the input list order. The caller must pass the samples in
     * ascending event-time order so velocity features are built correctly.
     */
    public double[] scoreAll(List<FraudSample> samples) {
        double[] scores = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            scores[i] = score(samples.get(i).transaction());
        }
        return scores;
    }
}
