package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * Transaction-frequency (velocity) rule.
 *
 * <p>Catches a burst of transactions on one card in a short window — the
 * signature of an automated card-testing or cash-out attack. It fires when the
 * number of prior transactions already seen for the card within the window
 * reaches {@code frequency.threshold}. Using event-time counts means a replayed
 * batch of old transactions is judged by their own timestamps, not by when they
 * happen to arrive.
 */
@Component
public class TransactionFrequencyRule implements Rule {

    public static final String ID = "VELOCITY_FREQUENCY";

    private final ScoringProperties properties;

    public TransactionFrequencyRule(ScoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Too many transactions in a short window";
    }

    @Override
    public RuleOutcome evaluate(RuleContext context) {
        int priorCount = context.snapshot().priorCountInWindow();
        int threshold = properties.frequency().threshold();
        if (priorCount < threshold) {
            return RuleOutcome.notFired(ID, name());
        }
        long windowMinutes = properties.window().toMinutes();
        String reason = String.format(
                "this is transaction %d for the card within %d minutes (threshold %d prior)",
                priorCount + 1, windowMinutes, threshold);
        return RuleOutcome.fired(ID, name(), properties.frequency().weight(), reason);
    }
}
