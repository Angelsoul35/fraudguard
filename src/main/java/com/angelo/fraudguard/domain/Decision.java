package com.angelo.fraudguard.domain;

/**
 * The action the service recommends for a scored transaction.
 *
 * <p>The decision is derived purely from the aggregated risk score by the
 * thresholds in {@code scoring.*} configuration, so it is reproducible: the
 * same score always maps to the same decision.
 */
public enum Decision {
    /** Low risk; let the transaction proceed. */
    APPROVE,
    /** Enough risk to warrant a human (or step-up) check before settling. */
    REVIEW,
    /** High risk; block the transaction. */
    DECLINE
}
