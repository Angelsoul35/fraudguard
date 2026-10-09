package com.angelo.fraudguard.benchmark;

import java.util.Arrays;

/**
 * Ranking and threshold metrics for a binary fraud classifier, implemented from
 * first principles so the benchmark has no external machine-learning dependency
 * and every number is reproducible and auditable.
 *
 * <p>All methods take a parallel array of real-valued {@code scores} (higher means
 * "more likely fraud") and boolean {@code labels} ({@code true} = fraud). Card
 * fraud is heavily imbalanced, so the headline metric is the area under the
 * precision-recall curve (average precision), complemented by ROC-AUC and
 * recall at a fixed precision. Ties in the score are handled correctly: ROC-AUC
 * uses mid-ranks (the Mann-Whitney U estimator) and average precision groups
 * equal scores into a single threshold, matching the reference behaviour of
 * scikit-learn's {@code roc_auc_score} and {@code average_precision_score}.
 */
public final class Metrics {

    private Metrics() {
    }

    /** Immutable bundle of the four headline figures for one labelled run. */
    public record Summary(double rocAuc, double averagePrecision, double recallAtPrecision,
                          double basePositiveRate) {
    }

    private static void checkParallel(double[] scores, boolean[] labels) {
        if (scores.length != labels.length) {
            throw new IllegalArgumentException("scores and labels differ in length: "
                    + scores.length + " vs " + labels.length);
        }
        if (scores.length == 0) {
            throw new IllegalArgumentException("need at least one observation");
        }
    }

    /** Fraction of observations that are positive (the fraud base rate). */
    public static double basePositiveRate(boolean[] labels) {
        if (labels.length == 0) {
            throw new IllegalArgumentException("need at least one observation");
        }
        long pos = 0;
        for (boolean l : labels) {
            if (l) {
                pos++;
            }
        }
        return (double) pos / labels.length;
    }

    /**
     * Area under the ROC curve via the Mann-Whitney U statistic: the probability
     * that a randomly chosen positive is ranked above a randomly chosen negative,
     * with tied scores counted as half. Perfect separation returns 1.0, a fully
     * inverted ranking returns 0.0, and an uninformative score returns ~0.5.
     */
    public static double rocAuc(double[] scores, boolean[] labels) {
        checkParallel(scores, labels);
        int n = scores.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(scores[a], scores[b]));

        // Assign mid-ranks (1-based) so tied scores share the average of their ranks.
        double[] rank = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && scores[order[j + 1]] == scores[order[i]]) {
                j++;
            }
            double midRank = (i + j) / 2.0 + 1.0; // average of ranks (i+1)..(j+1)
            for (int k = i; k <= j; k++) {
                rank[order[k]] = midRank;
            }
            i = j + 1;
        }

        double sumRankPos = 0.0;
        long nPos = 0;
        for (int k = 0; k < n; k++) {
            if (labels[k]) {
                sumRankPos += rank[k];
                nPos++;
            }
        }
        long nNeg = n - nPos;
        if (nPos == 0 || nNeg == 0) {
            throw new IllegalArgumentException("ROC-AUC is undefined without both classes present");
        }
        double u = sumRankPos - nPos * (nPos + 1) / 2.0;
        return u / ((double) nPos * nNeg);
    }

    /**
     * Average precision: the area under the precision-recall curve computed as
     * {@code sum((R_n - R_{n-1}) * P_n)} over decreasing score thresholds, where
     * equal scores are collapsed into one threshold. This is the imbalance-aware
     * headline: for an uninformative score it tends to the positive base rate,
     * and for perfect separation it is 1.0.
     */
    public static double averagePrecision(double[] scores, boolean[] labels) {
        checkParallel(scores, labels);
        int n = scores.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // Descending score: the operating point sweeps from the most suspicious down.
        Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));

        long totalPos = 0;
        for (boolean l : labels) {
            if (l) {
                totalPos++;
            }
        }
        if (totalPos == 0) {
            throw new IllegalArgumentException("average precision is undefined without any positives");
        }

        double ap = 0.0;
        double prevRecall = 0.0;
        long tp = 0;
        long fp = 0;
        int i = 0;
        while (i < n) {
            // Advance over every observation sharing this threshold score.
            int j = i;
            while (j < n && scores[order[j]] == scores[order[i]]) {
                if (labels[order[j]]) {
                    tp++;
                } else {
                    fp++;
                }
                j++;
            }
            double precision = (double) tp / (tp + fp);
            double recall = (double) tp / totalPos;
            ap += (recall - prevRecall) * precision;
            prevRecall = recall;
            i = j;
        }
        return ap;
    }

    /**
     * The highest recall attainable at any threshold whose precision is at least
     * {@code minPrecision}. This answers the operational question "if the review
     * queue must run at &gt;= p precision, what share of fraud do we still catch?".
     * Returns 0.0 when no threshold reaches the requested precision.
     */
    public static double recallAtPrecision(double[] scores, boolean[] labels, double minPrecision) {
        checkParallel(scores, labels);
        int n = scores.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));

        long totalPos = 0;
        for (boolean l : labels) {
            if (l) {
                totalPos++;
            }
        }
        if (totalPos == 0) {
            throw new IllegalArgumentException("recall@precision is undefined without any positives");
        }

        double best = 0.0;
        long tp = 0;
        long fp = 0;
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && scores[order[j]] == scores[order[i]]) {
                if (labels[order[j]]) {
                    tp++;
                } else {
                    fp++;
                }
                j++;
            }
            double precision = (double) tp / (tp + fp);
            double recall = (double) tp / totalPos;
            if (precision >= minPrecision && recall > best) {
                best = recall;
            }
            i = j;
        }
        return best;
    }

    /** Compute all four headline figures at the given precision floor in one pass bundle. */
    public static Summary summarize(double[] scores, boolean[] labels, double precisionFloor) {
        return new Summary(
                rocAuc(scores, labels),
                averagePrecision(scores, labels),
                recallAtPrecision(scores, labels, precisionFloor),
                basePositiveRate(labels));
    }
}
