package com.angelo.fraudguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.velocity.VelocitySnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionFrequencyRuleTest {

    private final TransactionFrequencyRule rule = new TransactionFrequencyRule(ScoringProperties.defaults());

    private static RuleContext context(int priorCount) {
        Transaction tx = new Transaction("t", "card-1", 1_000, "USD", "Shop", "US",
                Instant.parse("2026-01-01T00:00:00Z"));
        VelocitySnapshot snapshot = new VelocitySnapshot(priorCount, 1_000, 1_000,
                Optional.of("US"), Optional.of(Instant.parse("2025-12-31T23:59:00Z")), Set.of("US"));
        return new RuleContext(tx, snapshot);
    }

    @Test
    void fires_when_prior_count_reaches_threshold() {
        // Default threshold is 5 prior transactions in the window.
        RuleOutcome outcome = rule.evaluate(context(5));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(25);
        assertThat(outcome.reason()).contains("transaction 6");
    }

    @Test
    void does_not_fire_below_threshold() {
        RuleOutcome outcome = rule.evaluate(context(4));

        assertThat(outcome.fired()).isFalse();
        assertThat(outcome.contribution()).isZero();
    }
}
