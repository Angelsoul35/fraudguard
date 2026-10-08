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

class AmountVelocityRuleTest {

    private final AmountVelocityRule rule = new AmountVelocityRule(ScoringProperties.defaults());

    private static Transaction tx(long amount) {
        return new Transaction("t1", "card-1", amount, "USD", "Shop", "US", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static VelocitySnapshot snapshot(int priorCount, double average) {
        return new VelocitySnapshot(priorCount, average, (long) average, Optional.of("US"),
                Optional.of(Instant.parse("2025-12-31T23:59:00Z")), Set.of("US"));
    }

    @Test
    void fires_when_amount_far_above_recent_average_with_enough_history() {
        // average 2000 cents, 4 prior tx; a 20000-cent charge is 10x the average (>= 5x).
        RuleOutcome outcome = rule.evaluate(new RuleContext(tx(20_000), snapshot(4, 2_000)));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.ruleId()).isEqualTo(AmountVelocityRule.ID);
        assertThat(outcome.contribution()).isEqualTo(30);
        assertThat(outcome.reason()).contains("10.0x");
    }

    @Test
    void does_not_fire_when_amount_is_within_factor_of_average() {
        // 3x the average is below the 5x threshold.
        RuleOutcome outcome = rule.evaluate(new RuleContext(tx(6_000), snapshot(4, 2_000)));

        assertThat(outcome.fired()).isFalse();
        assertThat(outcome.contribution()).isZero();
    }

    @Test
    void does_not_fire_without_enough_history_even_on_a_huge_amount() {
        // Only 2 prior transactions; min-history is 3, so no baseline is trusted yet.
        RuleOutcome outcome = rule.evaluate(new RuleContext(tx(1_000_000), snapshot(2, 2_000)));

        assertThat(outcome.fired()).isFalse();
    }

    @Test
    void does_not_fire_on_a_first_seen_card() {
        VelocitySnapshot firstSeen = new VelocitySnapshot(0, 0, 0, Optional.empty(), Optional.empty(), Set.of());
        RuleOutcome outcome = rule.evaluate(new RuleContext(tx(1_000_000), firstSeen));

        assertThat(outcome.fired()).isFalse();
    }
}
