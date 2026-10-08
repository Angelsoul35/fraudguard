package com.angelo.fraudguard.scoring;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.Decision;
import com.angelo.fraudguard.domain.RuleOutcome;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a set of rule outcomes into a bounded risk score and a decision.
 *
 * <p>The score is the sum of the fired rules' contributions, clamped to
 * {@code [0, 100]}. Summation is deliberate and explainable: the assessment can
 * state exactly which rules produced the number and by how much. The decision is
 * then a pure function of the score and the configured thresholds, so it is
 * reproducible and easy to tune:
 *
 * <pre>
 *   score &gt;= declineThreshold  -&gt; DECLINE
 *   score &gt;= reviewThreshold   -&gt; REVIEW
 *   otherwise                  -&gt; APPROVE
 * </pre>
 */
@Component
public class RiskScorer {

    private final ScoringProperties properties;

    public RiskScorer(ScoringProperties properties) {
        this.properties = properties;
    }

    /** The aggregated, clamped risk score for the given outcomes. */
    public int score(List<RuleOutcome> outcomes) {
        int raw = outcomes.stream()
                .filter(RuleOutcome::fired)
                .mapToInt(RuleOutcome::contribution)
                .sum();
        return Math.max(0, Math.min(100, raw));
    }

    /** The decision implied by a score. */
    public Decision decide(int score) {
        if (score >= properties.declineThreshold()) {
            return Decision.DECLINE;
        }
        if (score >= properties.reviewThreshold()) {
            return Decision.REVIEW;
        }
        return Decision.APPROVE;
    }
}
