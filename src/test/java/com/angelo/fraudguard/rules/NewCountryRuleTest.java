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

class NewCountryRuleTest {

    private final NewCountryRule rule = new NewCountryRule(ScoringProperties.defaults());

    private static RuleContext context(String country, Set<String> seen) {
        Transaction tx = new Transaction("t", "card-1", 1_000, "USD", "Shop", country,
                Instant.parse("2026-01-01T00:00:00Z"));
        Optional<String> previous = seen.isEmpty() ? Optional.empty() : Optional.of(seen.iterator().next());
        Optional<Instant> previousTs = seen.isEmpty()
                ? Optional.empty() : Optional.of(Instant.parse("2025-12-31T23:00:00Z"));
        VelocitySnapshot snapshot = new VelocitySnapshot(0, 0, 0, previous, previousTs, seen);
        return new RuleContext(tx, snapshot);
    }

    @Test
    void fires_on_a_country_new_to_a_card_with_history() {
        RuleOutcome outcome = rule.evaluate(context("FR", Set.of("US", "CA")));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(20);
        assertThat(outcome.reason()).contains("FR");
    }

    @Test
    void fires_with_high_risk_weight_on_a_high_risk_country() {
        RuleOutcome outcome = rule.evaluate(context("RU", Set.of("US")));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(30);
        assertThat(outcome.reason()).contains("high-risk");
    }

    @Test
    void fires_on_high_risk_even_for_a_first_seen_card() {
        RuleOutcome outcome = rule.evaluate(context("KP", Set.of()));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(30);
    }

    @Test
    void does_not_fire_on_a_known_country() {
        RuleOutcome outcome = rule.evaluate(context("US", Set.of("US", "CA")));

        assertThat(outcome.fired()).isFalse();
    }

    @Test
    void does_not_fire_on_a_first_seen_card_in_an_ordinary_country() {
        // No history yet, and the country is not high-risk: novelty alone is not suspicious.
        RuleOutcome outcome = rule.evaluate(context("US", Set.of()));

        assertThat(outcome.fired()).isFalse();
    }
}
