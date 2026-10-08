package com.angelo.fraudguard.domain;

/**
 * The result of evaluating a single rule against a transaction.
 *
 * <p>Every rule returns a {@code RuleOutcome}, whether or not it fired. A
 * non-fired outcome carries a zero contribution and is dropped from the
 * assessment; a fired outcome carries its score contribution and a
 * human-readable {@code reason} explaining <em>why</em> it fired, so the
 * decision is always auditable.
 *
 * @param ruleId       stable machine identifier, e.g. {@code "VELOCITY_AMOUNT"}
 * @param ruleName     human-friendly rule name for display
 * @param fired        whether the rule's condition was met
 * @param contribution points this rule adds to the risk score when it fires (0 otherwise)
 * @param reason       explanation of the outcome, with the evidence that triggered it
 */
public record RuleOutcome(
        String ruleId,
        String ruleName,
        boolean fired,
        int contribution,
        String reason) {

    /** A convenience factory for a rule that did not fire. */
    public static RuleOutcome notFired(String ruleId, String ruleName) {
        return new RuleOutcome(ruleId, ruleName, false, 0, "rule did not fire");
    }

    /** A convenience factory for a rule that fired with the given contribution and reason. */
    public static RuleOutcome fired(String ruleId, String ruleName, int contribution, String reason) {
        return new RuleOutcome(ruleId, ruleName, true, contribution, reason);
    }
}
