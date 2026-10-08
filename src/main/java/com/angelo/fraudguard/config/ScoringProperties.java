package com.angelo.fraudguard.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable thresholds and per-rule weights, bound from {@code fraudguard.*} in
 * {@code application.yml}. Every operator dial lives here rather than hard-coded
 * in a rule, so behaviour can be tuned without a recompile and every number is
 * visible in one place.
 *
 * <p>Defaults are declared with {@link DefaultValue} so the object is valid even
 * when a key is missing, and {@link #defaults()} builds the same configuration
 * for unit tests.
 *
 * @param window           sliding window for velocity features (e.g. {@code PT10M})
 * @param reviewThreshold  score at or above which the decision is REVIEW
 * @param declineThreshold score at or above which the decision is DECLINE
 * @param velocity         amount-spike rule configuration
 * @param frequency        transaction-frequency rule configuration
 * @param travel           impossible-travel rule configuration
 * @param roundAmount       round-amount / high-value rule configuration
 * @param newCountry       new / high-risk country rule configuration
 */
@ConfigurationProperties(prefix = "fraudguard")
public record ScoringProperties(
        @DefaultValue("PT10M") Duration window,
        @DefaultValue("40") int reviewThreshold,
        @DefaultValue("70") int declineThreshold,
        @DefaultValue Velocity velocity,
        @DefaultValue Frequency frequency,
        @DefaultValue Travel travel,
        @DefaultValue RoundAmount roundAmount,
        @DefaultValue NewCountry newCountry) {

    /** Amount-spike rule: fires when amount >= factor * recent average, given enough history. */
    public record Velocity(
            @DefaultValue("5.0") double amountFactor,
            @DefaultValue("3") int minHistory,
            @DefaultValue("30") int weight) {
    }

    /** Frequency rule: fires when prior count in the window reaches the threshold. */
    public record Frequency(
            @DefaultValue("5") int threshold,
            @DefaultValue("25") int weight) {
    }

    /** Impossible-travel rule: fires when implied speed between countries exceeds the limit. */
    public record Travel(
            @DefaultValue("900.0") double maxSpeedKmh,
            @DefaultValue("40") int weight) {
    }

    /** Round-amount + high-value rule: fires on a round amount at or above the value floor. */
    public record RoundAmount(
            @DefaultValue("100000") long minValueCents,
            @DefaultValue("15") int weight) {
    }

    /** New / high-risk country rule: separate weights for a new country and a high-risk one. */
    public record NewCountry(
            @DefaultValue("20") int weight,
            @DefaultValue("30") int highRiskWeight) {
    }

    /** The built-in defaults, for tests that construct the configuration directly. */
    public static ScoringProperties defaults() {
        return new ScoringProperties(
                Duration.ofMinutes(10),
                40,
                70,
                new Velocity(5.0, 3, 30),
                new Frequency(5, 25),
                new Travel(900.0, 40),
                new RoundAmount(100_000, 15),
                new NewCountry(20, 30));
    }
}
