package com.angelo.fraudguard.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.angelo.fraudguard.benchmark.Metrics.Summary;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The real-data benchmark, asserted as a test. It runs FraudGuard's production
 * scorer over the vendored Sparkov snapshot through {@link BenchmarkEvaluator} and
 * checks the properties that matter for a credible result:
 *
 * <ul>
 *   <li>the scorer beats the majority/random baseline on the held-out cards;</li>
 *   <li>cross-validation and the held-out test agree, so the signal generalises;</li>
 *   <li>the shuffled-label control collapses to chance (no leakage);</li>
 *   <li>the verdict logic discriminates — the shuffled run is never WELL_FIT.</li>
 * </ul>
 *
 * The printed report is the headline block quoted in the README.
 */
class RealDataBenchmarkTest {

    @Test
    void scorer_beats_baseline_generalises_and_the_control_collapses() {
        List<FraudSample> samples = FraudDatasetLoader.loadDefault();
        EvaluationReport report =
                BenchmarkEvaluator.defaults().evaluate(samples, RealDataBenchmark.benchmarkConfig());
        System.out.println(report.format());

        // Data shape.
        assertThat(report.totalSamples()).isEqualTo(150_000);
        assertThat(report.overallBaseRate()).isBetween(0.005, 0.02);
        assertThat(report.heldoutFraud()).isGreaterThan(50);

        Summary heldout = report.heldoutSummary();

        // The scorer clearly beats both baselines on unseen cards:
        //   random ROC-AUC = 0.5, majority PR-AUC = base rate.
        assertThat(heldout.rocAuc()).isGreaterThan(0.60);
        assertThat(heldout.averagePrecision())
                .isGreaterThan(heldout.basePositiveRate() * 2.0);

        // At the 10% precision operating point it recovers a large share of fraud,
        // an order of magnitude above the base rate caught by random review.
        assertThat(heldout.recallAtPrecision()).isGreaterThan(0.20);

        // Cross-validation and held-out agree (generalisation, not luck of a split).
        double disagreement = Math.abs(report.cvRocMean() - heldout.rocAuc());
        assertThat(disagreement).isLessThan(Math.max(3 * report.cvRocStd(), 0.05));

        // Negative control: shuffling the labels destroys the signal.
        assertThat(report.shuffledSummary().rocAuc()).isCloseTo(0.5, within(0.05));
        assertThat(report.shuffledSummary().averagePrecision())
                .isLessThan(heldout.basePositiveRate() * 1.75 + 0.002);
        assertThat(report.controlCollapsed()).isTrue();

        // Overall verdict.
        assertThat(report.verdict()).isEqualTo(Verdict.WELL_FIT);
    }

    @Test
    void the_verdict_logic_does_not_call_a_shuffled_run_well_fit() {
        // Use the real held-out scores but shuffled labels: the verdict must refuse
        // to call that WELL_FIT. This is the guard against a broken evaluation
        // flattering the model.
        List<FraudSample> samples = FraudDatasetLoader.loadDefault();
        EvaluationReport report =
                BenchmarkEvaluator.defaults().evaluate(samples, RealDataBenchmark.benchmarkConfig());

        Summary shuffled = report.shuffledSummary();
        Verdict onShuffled =
                FitAssessment.judge(shuffled.basePositiveRate(), 0.5, 0.01, shuffled);
        assertThat(onShuffled).isNotEqualTo(Verdict.WELL_FIT);

        // And on the genuine run it IS well fit, so the two are actually distinguished.
        assertThat(report.verdict()).isEqualTo(Verdict.WELL_FIT);
    }
}
