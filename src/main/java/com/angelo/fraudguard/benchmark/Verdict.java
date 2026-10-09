package com.angelo.fraudguard.benchmark;

/**
 * The three possible conclusions about how FraudGuard's scorer fits the data.
 *
 * <ul>
 *   <li>{@link #WELL_FIT} — the held-out performance clears the baseline and
 *       agrees with cross-validation, so the signal generalises.</li>
 *   <li>{@link #OVERFIT} — cross-validation and held-out disagree beyond the
 *       cross-validation noise: performance does not carry to unseen cards.</li>
 *   <li>{@link #UNDERFIT} — held-out performance does not clear the baseline, so
 *       there is no generalising signal to speak of.</li>
 * </ul>
 */
public enum Verdict {
    WELL_FIT,
    OVERFIT,
    UNDERFIT
}
