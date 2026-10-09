package com.angelo.fraudguard.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.benchmark.Metrics.Summary;
import org.junit.jupiter.api.Test;

/**
 * The verdict logic must discriminate: real, generalising signal is WELL_FIT; a
 * run with no held-out signal (as a shuffled-label control produces) is never
 * WELL_FIT; and a run whose held-out score falls far below the cross-validation
 * mean is OVERFIT. These are the guards that stop the benchmark from quietly
 * declaring a broken model healthy.
 */
class FitAssessmentTest {

    private static final double BASE = 0.006;

    @Test
    void generalising_signal_is_well_fit() {
        Summary heldout = new Summary(0.79, 0.10, 0.30, BASE);
        assertThat(FitAssessment.judge(BASE, 0.80, 0.02, heldout)).isEqualTo(Verdict.WELL_FIT);
    }

    @Test
    void a_shuffled_control_is_not_well_fit() {
        // ROC at chance and AP at the base rate: no signal -> UNDERFIT, crucially not WELL_FIT.
        Summary shuffled = new Summary(0.50, BASE, 0.00, BASE);
        Verdict v = FitAssessment.judge(BASE, 0.50, 0.01, shuffled);
        assertThat(v).isNotEqualTo(Verdict.WELL_FIT);
        assertThat(v).isEqualTo(Verdict.UNDERFIT);
    }

    @Test
    void held_out_far_below_cross_validation_is_overfit() {
        // Clears the baseline but disagrees with CV well beyond the fold noise.
        Summary heldout = new Summary(0.65, 0.05, 0.10, BASE);
        assertThat(FitAssessment.judge(BASE, 0.90, 0.01, heldout)).isEqualTo(Verdict.OVERFIT);
    }

    @Test
    void weak_held_out_is_underfit_even_if_cv_agrees() {
        Summary heldout = new Summary(0.55, 0.008, 0.0, BASE);
        assertThat(FitAssessment.judge(BASE, 0.55, 0.01, heldout)).isEqualTo(Verdict.UNDERFIT);
    }

    @Test
    void control_collapse_detector_accepts_chance_and_rejects_signal() {
        assertThat(FitAssessment.controlCollapsed(BASE, new Summary(0.50, BASE, 0.0, BASE))).isTrue();
        assertThat(FitAssessment.controlCollapsed(BASE, new Summary(0.49, BASE * 1.2, 0.0, BASE))).isTrue();
        // A control that did not collapse (ROC still high) must be rejected.
        assertThat(FitAssessment.controlCollapsed(BASE, new Summary(0.75, 0.08, 0.2, BASE))).isFalse();
    }
}
