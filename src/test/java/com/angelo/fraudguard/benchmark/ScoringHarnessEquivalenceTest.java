package com.angelo.fraudguard.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.events.EventBus;
import com.angelo.fraudguard.events.InMemoryEventBus;
import com.angelo.fraudguard.rules.AmountVelocityRule;
import com.angelo.fraudguard.rules.ImpossibleTravelRule;
import com.angelo.fraudguard.rules.NewCountryRule;
import com.angelo.fraudguard.rules.RoundAmountHighValueRule;
import com.angelo.fraudguard.rules.Rule;
import com.angelo.fraudguard.rules.RuleEngine;
import com.angelo.fraudguard.rules.TransactionFrequencyRule;
import com.angelo.fraudguard.scoring.InMemoryAssessmentStore;
import com.angelo.fraudguard.scoring.RiskScorer;
import com.angelo.fraudguard.scoring.ScoringService;
import com.angelo.fraudguard.velocity.InMemoryVelocityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ScoringHarness} to the production {@link ScoringService}: for the
 * same transaction sequence, scored in the same order, the harness must produce
 * exactly the score the real pipeline stores. This is what lets the benchmark
 * claim it reuses the scorer end to end — if someone changed the harness to take
 * a shortcut, this test would fail.
 */
class ScoringHarnessEquivalenceTest {

    private static ScoringService newProductionService(ScoringProperties props) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        EventBus bus = new InMemoryEventBus(executor);
        InMemoryVelocityStore velocityStore = new InMemoryVelocityStore(props);
        List<Rule> rules = List.of(
                new AmountVelocityRule(props),
                new TransactionFrequencyRule(props),
                new ImpossibleTravelRule(props),
                new RoundAmountHighValueRule(props),
                new NewCountryRule(props));
        RuleEngine engine = new RuleEngine(rules);
        RiskScorer scorer = new RiskScorer(props);
        return new ScoringService(bus, velocityStore, engine, scorer, new InMemoryAssessmentStore());
    }

    @Test
    void harness_reproduces_scoring_service_scores_transaction_by_transaction() {
        ScoringProperties props = ScoringProperties.defaults();
        ScoringService production = newProductionService(props);
        ScoringHarness harness = new ScoringHarness(props);

        Instant t0 = Instant.parse("2026-02-01T09:00:00Z");
        List<Transaction> sequence = new ArrayList<>();
        // A velocity baseline, an amount spike, a burst, a new high-risk country:
        // a sequence that makes several different rules fire and exercises velocity.
        for (int i = 0; i < 6; i++) {
            sequence.add(new Transaction("base-" + i, "card-A", 2_000, "USD", "Shop", "US",
                    t0.plus(Duration.ofMinutes(i))));
        }
        sequence.add(new Transaction("spike", "card-A", 100_000, "USD", "Shop", "US",
                t0.plus(Duration.ofMinutes(6))));
        sequence.add(new Transaction("risk", "card-A", 5_000, "USD", "Shop", "RU",
                t0.plus(Duration.ofMinutes(7))));
        sequence.add(new Transaction("fresh", "card-B", 4_200, "USD", "Shop", "US", t0));

        boolean anyNonZero = false;
        for (Transaction tx : sequence) {
            int fromHarness = harness.score(tx);
            int fromProduction = production.score(tx).score();
            assertThat(fromHarness)
                    .as("score for transaction %s", tx.id())
                    .isEqualTo(fromProduction);
            anyNonZero |= fromHarness > 0;
        }
        // Guard against a degenerate "always zero" agreement.
        assertThat(anyNonZero).as("the sequence should make at least one rule fire").isTrue();
    }
}
