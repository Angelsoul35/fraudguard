package com.angelo.fraudguard.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.Decision;
import com.angelo.fraudguard.domain.RuleOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer(ScoringProperties.defaults());

    private static RuleOutcome fired(int contribution) {
        return RuleOutcome.fired("R", "rule", contribution, "because");
    }

    @Test
    void score_sums_only_fired_contributions() {
        int score = scorer.score(List.of(
                fired(30),
                RuleOutcome.notFired("X", "x"),
                fired(25)));

        assertThat(score).isEqualTo(55);
    }

    @Test
    void score_is_clamped_to_100() {
        int score = scorer.score(List.of(fired(40), fired(40), fired(40)));

        assertThat(score).isEqualTo(100);
    }

    @Test
    void empty_or_all_not_fired_scores_zero() {
        assertThat(scorer.score(List.of())).isZero();
        assertThat(scorer.score(List.of(RuleOutcome.notFired("X", "x")))).isZero();
    }

    @Test
    void decision_thresholds_map_score_to_action() {
        assertThat(scorer.decide(0)).isEqualTo(Decision.APPROVE);
        assertThat(scorer.decide(39)).isEqualTo(Decision.APPROVE);
        assertThat(scorer.decide(40)).isEqualTo(Decision.REVIEW);   // review-threshold
        assertThat(scorer.decide(69)).isEqualTo(Decision.REVIEW);
        assertThat(scorer.decide(70)).isEqualTo(Decision.DECLINE);  // decline-threshold
        assertThat(scorer.decide(100)).isEqualTo(Decision.DECLINE);
    }
}
