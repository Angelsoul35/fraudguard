package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.benchmark.Metrics.Summary;
import com.angelo.fraudguard.config.ScoringProperties;
import java.util.List;
import java.util.Locale;

/**
 * The full result of a benchmark run: the sizes of every split, the baseline, the
 * cross-validation spread, the held-out and development figures, the shuffled-label
 * control, and the derived {@link Verdict}. {@link #format()} renders it as the
 * block of text printed by the benchmark and quoted in the README.
 */
public record EvaluationReport(
        ScoringProperties config,
        int folds,
        double precisionFloor,
        int totalSamples,
        int totalFraud,
        double overallBaseRate,
        int devSamples,
        int devFraud,
        int heldoutSamples,
        int heldoutFraud,
        List<Summary> foldSummaries,
        double cvRocMean, double cvRocStd,
        double cvApMean, double cvApStd,
        double cvRecallMean, double cvRecallStd,
        Summary devSummary,
        Summary heldoutSummary,
        Summary shuffledSummary,
        boolean controlCollapsed,
        Verdict verdict) {

    /** Human-readable headline block, with every number the write-up relies on. */
    public String format() {
        StringBuilder b = new StringBuilder();
        var p = (java.util.function.BiConsumer<String, Object[]>)
                (fmt, args) -> b.append(String.format(Locale.ROOT, fmt, args)).append('\n');

        p.accept("FraudGuard real-data benchmark", new Object[] {});
        p.accept("  dataset ...........: Sparkov Credit Card Transactions Fraud Detection (vendored snapshot)", new Object[] {});
        p.accept("  samples ...........: %,d transactions, %,d fraud (base rate %.4f = %.3f%%)",
                new Object[] {totalSamples, totalFraud, overallBaseRate, overallBaseRate * 100});
        p.accept("  split .............: %,d dev (%,d fraud) / %,d held-out (%,d fraud), card-disjoint",
                new Object[] {devSamples, devFraud, heldoutSamples, heldoutFraud});
        p.accept("  window / review / decline : %s / %d / %d",
                new Object[] {config.window(), config.reviewThreshold(), config.declineThreshold()});
        p.accept("  precision floor for recall@precision : %.2f", new Object[] {precisionFloor});
        p.accept("", new Object[] {});
        p.accept("  baseline (majority / random):", new Object[] {});
        p.accept("    PR-AUC  = base rate   = %.4f", new Object[] {overallBaseRate});
        p.accept("    ROC-AUC = 0.5000", new Object[] {});
        p.accept("", new Object[] {});
        p.accept("  %d-fold cross-validation over development cards (mean +/- std):", new Object[] {folds});
        p.accept("    PR-AUC  (avg precision) = %.4f +/- %.4f", new Object[] {cvApMean, cvApStd});
        p.accept("    ROC-AUC                 = %.4f +/- %.4f", new Object[] {cvRocMean, cvRocStd});
        p.accept("    recall@precision        = %.4f +/- %.4f", new Object[] {cvRecallMean, cvRecallStd});
        p.accept("", new Object[] {});
        p.accept("  development pool (all dev cards):", new Object[] {});
        p.accept("    PR-AUC = %.4f   ROC-AUC = %.4f   recall@precision = %.4f",
                new Object[] {devSummary.averagePrecision(), devSummary.rocAuc(), devSummary.recallAtPrecision()});
        p.accept("", new Object[] {});
        p.accept("  HELD-OUT TEST (unseen cards):", new Object[] {});
        p.accept("    PR-AUC  (avg precision) = %.4f   (%.1fx the base rate of %.4f)",
                new Object[] {heldoutSummary.averagePrecision(),
                        heldoutSummary.averagePrecision() / heldoutSummary.basePositiveRate(),
                        heldoutSummary.basePositiveRate()});
        p.accept("    ROC-AUC                 = %.4f", new Object[] {heldoutSummary.rocAuc()});
        p.accept("    recall@precision(%.2f)  = %.4f",
                new Object[] {precisionFloor, heldoutSummary.recallAtPrecision()});
        p.accept("", new Object[] {});
        p.accept("  CV<->held-out agreement : |%.4f - %.4f| = %.4f  (tolerance %.4f)",
                new Object[] {cvRocMean, heldoutSummary.rocAuc(), Math.abs(cvRocMean - heldoutSummary.rocAuc()),
                        Math.max(3.0 * cvRocStd, FitAssessment.AGREEMENT_TOLERANCE)});
        p.accept("  development - held-out ROC gap : %+.4f",
                new Object[] {devSummary.rocAuc() - heldoutSummary.rocAuc()});
        p.accept("", new Object[] {});
        p.accept("  NEGATIVE CONTROL (labels shuffled on the held-out set):", new Object[] {});
        p.accept("    PR-AUC  = %.4f  (-> base rate %.4f)",
                new Object[] {shuffledSummary.averagePrecision(), heldoutSummary.basePositiveRate()});
        p.accept("    ROC-AUC = %.4f  (-> 0.5)", new Object[] {shuffledSummary.rocAuc()});
        p.accept("    collapsed to chance : %s", new Object[] {controlCollapsed ? "YES" : "NO"});
        p.accept("", new Object[] {});
        p.accept("  VERDICT : %s", new Object[] {verdict});
        return b.toString();
    }
}
