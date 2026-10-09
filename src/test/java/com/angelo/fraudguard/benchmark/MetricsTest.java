package com.angelo.fraudguard.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Correctness tests for the metric implementations, anchored to values computed
 * by hand (and matching scikit-learn's reference behaviour). Each assertion fails
 * if the corresponding computation is wrong, so the metrics cannot silently drift.
 */
class MetricsTest {

    @Test
    void roc_auc_is_one_for_perfect_separation_and_zero_when_inverted() {
        double[] scores = {1, 2, 3, 4};
        boolean[] separable = {false, false, true, true}; // positives are the highest scores
        boolean[] inverted = {true, true, false, false};  // positives are the lowest scores

        assertThat(Metrics.rocAuc(scores, separable)).isEqualTo(1.0);
        assertThat(Metrics.rocAuc(scores, inverted)).isEqualTo(0.0);
    }

    @Test
    void average_precision_is_one_for_perfect_separation() {
        double[] scores = {1, 2, 3, 4};
        boolean[] separable = {false, false, true, true};
        assertThat(Metrics.averagePrecision(scores, separable)).isEqualTo(1.0);
    }

    @Test
    void roc_auc_matches_hand_computed_value_with_a_ranking_error() {
        // positives {0.9,0.7}, negatives {0.8,0.6}: 3 of 4 pairs correctly ordered.
        double[] scores = {0.9, 0.8, 0.7, 0.6};
        boolean[] labels = {true, false, true, false};
        assertThat(Metrics.rocAuc(scores, labels)).isEqualTo(0.75);
    }

    @Test
    void average_precision_matches_hand_computed_value() {
        // Thresholds contribute 0.5 (at 0.9) + 0.333... (at 0.7) = 0.8333...
        double[] scores = {0.9, 0.8, 0.7, 0.6};
        boolean[] labels = {true, false, true, false};
        assertThat(Metrics.averagePrecision(scores, labels)).isCloseTo(0.8333333, within(1e-6));
    }

    @Test
    void tied_scores_give_chance_roc_and_base_rate_average_precision() {
        double[] scores = {1, 1, 1, 1};
        boolean[] labels = {true, false, true, false};
        assertThat(Metrics.rocAuc(scores, labels)).isEqualTo(0.5);
        assertThat(Metrics.averagePrecision(scores, labels)).isEqualTo(0.5);
        assertThat(Metrics.basePositiveRate(labels)).isEqualTo(0.5);
    }

    @Test
    void recall_at_precision_picks_the_best_reachable_recall() {
        double[] scores = {0.9, 0.8, 0.7, 0.6};
        boolean[] labels = {true, false, true, false};
        // At precision 1.0 only the top point qualifies (recall 0.5).
        assertThat(Metrics.recallAtPrecision(scores, labels, 1.0)).isEqualTo(0.5);
        // At precision >= 0.6 the second positive is reachable (recall 1.0).
        assertThat(Metrics.recallAtPrecision(scores, labels, 0.6)).isEqualTo(1.0);
        // No threshold reaches precision above 1.0.
        assertThat(Metrics.recallAtPrecision(scores, labels, 1.01)).isEqualTo(0.0);
    }

    @Test
    void base_rate_counts_positives() {
        assertThat(Metrics.basePositiveRate(new boolean[] {true, false, false, false})).isEqualTo(0.25);
    }

    @Test
    void metrics_reject_degenerate_or_mismatched_input() {
        assertThatThrownBy(() -> Metrics.rocAuc(new double[] {1, 2}, new boolean[] {true}))
                .isInstanceOf(IllegalArgumentException.class);
        // ROC-AUC needs both classes present.
        assertThatThrownBy(() -> Metrics.rocAuc(new double[] {1, 2}, new boolean[] {true, true}))
                .isInstanceOf(IllegalArgumentException.class);
        // Average precision needs at least one positive.
        assertThatThrownBy(() -> Metrics.averagePrecision(new double[] {1, 2}, new boolean[] {false, false}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
