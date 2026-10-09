package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.benchmark.Metrics.Summary;

/**
 * Turns the measured metrics into a {@link Verdict}. The rule deliberately judges
 * generalisation by the <em>agreement</em> between cross-validation and the
 * held-out test rather than by any train-versus-test gap: FraudGuard's scorer has
 * no parameters fitted to data (it is a fixed, explainable expert system), so the
 * question is not "did it memorise the training rows" but "is the signal it
 * encodes stable across unseen cards and clearly above chance".
 *
 * <p>The function is pure so it can be exercised directly: a run with real signal
 * must be judged {@link Verdict#WELL_FIT}, a shuffled-label run (ROC collapsing to
 * ~0.5) must not, and a run whose held-out score falls far below its
 * cross-validation mean must be judged {@link Verdict#OVERFIT}.
 */
public final class FitAssessment {

    /** Held-out ROC-AUC must clear this to count as real signal (0.5 is chance). */
    public static final double MIN_ROC_AUC = 0.60;

    /** Held-out average precision must be at least this multiple of the base rate. */
    public static final double MIN_AP_LIFT = 2.0;

    /** Permitted gap between the cross-validation ROC mean and the held-out ROC. */
    public static final double AGREEMENT_TOLERANCE = 0.05;

    private FitAssessment() {
    }

    /**
     * @param baseRate    positive (fraud) base rate of the evaluation data
     * @param cvRocMean   mean ROC-AUC across the cross-validation folds
     * @param cvRocStd    standard deviation of ROC-AUC across the folds
     * @param heldout     metrics on the held-out test set
     * @return the fit verdict
     */
    public static Verdict judge(double baseRate, double cvRocMean, double cvRocStd, Summary heldout) {
        boolean beatsBaseline =
                heldout.rocAuc() >= MIN_ROC_AUC
                        && heldout.averagePrecision() >= MIN_AP_LIFT * baseRate;
        if (!beatsBaseline) {
            return Verdict.UNDERFIT;
        }
        double disagreement = Math.abs(cvRocMean - heldout.rocAuc());
        double tolerance = Math.max(3.0 * cvRocStd, AGREEMENT_TOLERANCE);
        if (disagreement > tolerance) {
            return Verdict.OVERFIT;
        }
        return Verdict.WELL_FIT;
    }

    /**
     * Whether a shuffled-label control collapsed to chance as it must: ROC-AUC
     * near 0.5 and average precision near the base rate. A control that does not
     * collapse signals leakage in the evaluation and invalidates the headline.
     */
    public static boolean controlCollapsed(double baseRate, Summary shuffled) {
        boolean rocNearChance = Math.abs(shuffled.rocAuc() - 0.5) <= 0.05;
        boolean apNearBase = shuffled.averagePrecision() <= baseRate * 1.75 + 0.002;
        return rocNearChance && apNearBase;
    }
}
