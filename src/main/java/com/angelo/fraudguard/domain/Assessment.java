package com.angelo.fraudguard.domain;

import java.time.Instant;
import java.util.List;

/**
 * The fraud assessment produced for a transaction: the aggregated risk score,
 * the resulting decision, and the list of rules that fired with their reasons.
 *
 * <p>Only the rules that actually fired are kept in {@code firedRules}: the
 * assessment is an explanation, not a dump of every rule that was evaluated.
 *
 * @param transactionId the transaction this assessment is for
 * @param cardId        the card the transaction belonged to
 * @param score         the aggregated risk score, 0 (safe) .. 100 (certain fraud)
 * @param decision      the recommended action derived from the score
 * @param firedRules    the rules that fired, each with its contribution and reason
 * @param assessedAt    when the assessment was produced (processing time)
 */
public record Assessment(
        String transactionId,
        String cardId,
        int score,
        Decision decision,
        List<RuleOutcome> firedRules,
        Instant assessedAt) {

    public Assessment {
        firedRules = List.copyOf(firedRules);
    }
}
