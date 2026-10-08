package com.angelo.fraudguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.velocity.VelocitySnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImpossibleTravelRuleTest {

    private final ImpossibleTravelRule rule = new ImpossibleTravelRule(ScoringProperties.defaults());

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static RuleContext context(String currentCountry, String previousCountry, Duration ago) {
        Transaction tx = new Transaction("t", "card-1", 1_000, "USD", "Shop", currentCountry, NOW);
        Optional<String> prev = Optional.ofNullable(previousCountry);
        Optional<Instant> prevTs = previousCountry == null ? Optional.empty() : Optional.of(NOW.minus(ago));
        VelocitySnapshot snapshot = new VelocitySnapshot(1, 1_000, 1_000, prev, prevTs,
                previousCountry == null ? Set.of() : Set.of(previousCountry));
        return new RuleContext(tx, snapshot);
    }

    @Test
    void fires_when_two_distant_countries_are_minutes_apart() {
        // US -> JP (~10,000 km) in 10 minutes implies a physically impossible speed.
        RuleOutcome outcome = rule.evaluate(context("JP", "US", Duration.ofMinutes(10)));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.contribution()).isEqualTo(40);
        assertThat(outcome.reason()).contains("US").contains("JP").contains("km/h");
    }

    @Test
    void fires_when_distant_countries_share_the_same_instant() {
        RuleOutcome outcome = rule.evaluate(context("JP", "US", Duration.ZERO));

        assertThat(outcome.fired()).isTrue();
        assertThat(outcome.reason()).contains("same time");
    }

    @Test
    void does_not_fire_when_there_is_enough_time_to_travel() {
        // US -> JP over 20 hours is a normal long-haul flight.
        RuleOutcome outcome = rule.evaluate(context("JP", "US", Duration.ofHours(20)));

        assertThat(outcome.fired()).isFalse();
    }

    @Test
    void does_not_fire_for_the_same_country() {
        RuleOutcome outcome = rule.evaluate(context("US", "US", Duration.ofMinutes(1)));

        assertThat(outcome.fired()).isFalse();
    }

    @Test
    void does_not_fire_without_a_previous_transaction() {
        RuleOutcome outcome = rule.evaluate(context("JP", null, Duration.ZERO));

        assertThat(outcome.fired()).isFalse();
    }
}
