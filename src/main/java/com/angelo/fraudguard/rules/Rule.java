package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.domain.RuleOutcome;

/**
 * One explainable fraud rule.
 *
 * <p>A rule is a pure function of its {@link RuleContext}: given the transaction
 * and the pre-transaction velocity snapshot it returns a {@link RuleOutcome}
 * that is either not-fired (zero contribution) or fired (a positive contribution
 * plus a human-readable reason). Rules never mutate shared state and never talk
 * to each other, so each can be reasoned about and tested in isolation.
 */
public interface Rule {

    /** Stable machine identifier, e.g. {@code "IMPOSSIBLE_TRAVEL"}. */
    String id();

    /** Human-friendly name for display in the assessment. */
    String name();

    /** Evaluate the rule against the context. */
    RuleOutcome evaluate(RuleContext context);
}
