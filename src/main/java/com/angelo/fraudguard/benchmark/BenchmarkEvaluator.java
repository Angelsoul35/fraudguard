package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.benchmark.Metrics.Summary;
import com.angelo.fraudguard.config.ScoringProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runs the full evaluation of FraudGuard's scorer over a labelled dataset:
 * k-fold cross-validation over development cards, a held-out test on unseen
 * cards, a shuffled-label negative control, and the derived fit {@link Verdict}.
 *
 * <p>Every split is scored with a <em>fresh</em> {@link ScoringHarness} so velocity
 * state never leaks between splits, and samples are fed in event-time order. The
 * scorer itself is the unmodified production pipeline (see {@link ScoringHarness}).
 */
public final class BenchmarkEvaluator {

    private final int folds;
    private final double heldoutFraction;
    private final double precisionFloor;
    private final long seed;

    public BenchmarkEvaluator(int folds, double heldoutFraction, double precisionFloor, long seed) {
        this.folds = folds;
        this.heldoutFraction = heldoutFraction;
        this.precisionFloor = precisionFloor;
        this.seed = seed;
    }

    /**
     * A sensible default: 5-fold CV, 25% of cards held out, and recall reported at a
     * 10% precision operating point. Ten percent precision is an order of magnitude
     * above the ~0.9% fraud base rate — a realistic review-queue target for a
     * first-line scorer, and the band this rule engine actually reaches.
     */
    public static BenchmarkEvaluator defaults() {
        return new BenchmarkEvaluator(5, 0.25, 0.10, 20240217L);
    }

    public EvaluationReport evaluate(List<FraudSample> samples, ScoringProperties config) {
        DatasetSplit split = DatasetSplit.byCard(samples, folds, heldoutFraction, seed);

        // Cross-validation: one fresh harness per fold.
        List<Summary> foldSummaries = new ArrayList<>();
        for (List<FraudSample> fold : split.folds()) {
            foldSummaries.add(summarize(fold, config));
        }
        double[] rocs = foldSummaries.stream().mapToDouble(Summary::rocAuc).toArray();
        double[] aps = foldSummaries.stream().mapToDouble(Summary::averagePrecision).toArray();
        double[] recs = foldSummaries.stream().mapToDouble(Summary::recallAtPrecision).toArray();

        Summary dev = summarize(split.development(), config);

        // Held-out test: fresh harness, score, then build the shuffled control on the
        // very same scores so only the label alignment differs.
        List<FraudSample> heldout = split.heldout();
        ScoringHarness harness = new ScoringHarness(config);
        double[] heldoutScores = harness.scoreAll(heldout);
        boolean[] heldoutLabels = labels(heldout);
        Summary heldoutSummary = Metrics.summarize(heldoutScores, heldoutLabels, precisionFloor);

        boolean[] shuffled = shuffleLabels(heldoutLabels, seed);
        Summary shuffledSummary = Metrics.summarize(heldoutScores, shuffled, precisionFloor);

        double cvRocMean = mean(rocs);
        double cvRocStd = std(rocs, cvRocMean);
        Verdict verdict = FitAssessment.judge(
                heldoutSummary.basePositiveRate(), cvRocMean, cvRocStd, heldoutSummary);
        boolean controlCollapsed =
                FitAssessment.controlCollapsed(heldoutSummary.basePositiveRate(), shuffledSummary);

        int totalFraud = countFraud(samples);
        return new EvaluationReport(
                config, folds, precisionFloor,
                samples.size(), totalFraud, (double) totalFraud / samples.size(),
                split.development().size(), countFraud(split.development()),
                heldout.size(), countFraud(heldout),
                foldSummaries,
                cvRocMean, cvRocStd,
                mean(aps), std(aps, mean(aps)),
                mean(recs), std(recs, mean(recs)),
                dev, heldoutSummary, shuffledSummary, controlCollapsed, verdict);
    }

    private Summary summarize(List<FraudSample> samples, ScoringProperties config) {
        ScoringHarness harness = new ScoringHarness(config);
        double[] scores = harness.scoreAll(samples);
        return Metrics.summarize(scores, labels(samples), precisionFloor);
    }

    private static boolean[] labels(List<FraudSample> samples) {
        boolean[] y = new boolean[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            y[i] = samples.get(i).fraud();
        }
        return y;
    }

    private static boolean[] shuffleLabels(boolean[] labels, long seed) {
        boolean[] copy = labels.clone();
        Random rnd = new Random(seed * 31 + 7);
        for (int i = copy.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            boolean t = copy[i];
            copy[i] = copy[j];
            copy[j] = t;
        }
        return copy;
    }

    private static int countFraud(List<FraudSample> samples) {
        int n = 0;
        for (FraudSample s : samples) {
            if (s.fraud()) {
                n++;
            }
        }
        return n;
    }

    private static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    private static double std(double[] xs, double mean) {
        double s = 0;
        for (double x : xs) {
            s += (x - mean) * (x - mean);
        }
        return Math.sqrt(s / xs.length);
    }
}
