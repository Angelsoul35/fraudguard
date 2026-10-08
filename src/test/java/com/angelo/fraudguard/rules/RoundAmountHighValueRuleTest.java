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

class RoundAmountHighValueRuleTest {

    private final RoundAmountHighValueRule rule = new RoundAmountHighValueRule(ScoringProperties.defaults());

    private static RuleContext context(long amountCents) {
        Transaction tx = new Transaction("t", "card-1", amountCents, "USD", "Shop", "US",
                Instant.parse("2026-01-01T00:00:00Z"));
        VelocitySnapshot snapshot = new VelocitySnapshot(0, 0, 0, Optional.empty(), Optional.empty(), Set.of());
        return new RuleContext(tx, snapshot);
    }

    @Test
    void fires_on_a_round_high_value_amount() {
        // 5000.00 -> 500000 cents: at/above 1000.00 and an exact multiple of 100.00.
        RuleOutcome outcome = rule.evaluate(context(500_000));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(15);
        assertThat(outcome.reason()).contains("5000.00");
    }

    @Test
    void does_not_fire_when_the_amount_is_not_round() {
        // 1234.56 is high-value but not a round multiple of 100.00.
        RuleOutcome outcome = rule.evaluate(context(123_456));

        assertThat(outcome.fired()).isFalse();
    }

    @Test
    void does_not_fire_when_round_but_below_the_value_floor() {
        // 200.00 is round but below the 1000.00 floor.
        RuleOutcome outcome = rule.evaluate(context(20_000));

        assertThat(outcome.fired()).isFalse();
    }
}
