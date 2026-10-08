package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * Amount-spike (velocity) rule.
 *
 * <p>Catches a single charge that is far larger than the card's own recent
 * behaviour — a classic sign of a tested card being cashed out. It fires when
 * the card has at least {@code velocity.minHistory} prior transactions in the
 * window and the current amount is at least {@code velocity.amountFactor} times
 * their average. Requiring a minimum history stops the rule from firing on a
 * card's very first, baseline-setting purchases.
 */
@Component
public class AmountVelocityRule implements Rule {

    public static final String ID = "VELOCITY_AMOUNT";

    private final ScoringProperties.Velocity config;

    public AmountVelocityRule(ScoringProperties properties) {
        this.config = properties.velocity();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Amount far above recent average";
    }

    @Override
    public RuleOutcome evaluate(RuleContext context) {
        var snapshot = context.snapshot();
        long amount = context.transaction().amount();

        if (snapshot.priorCountInWindow() < config.minHistory() || snapshot.averageAmountInWindow() <= 0) {
            return RuleOutcome.notFired(ID, name());
        }
        double ratio = amount / snapshot.averageAmountInWindow();
        if (ratio < config.amountFactor()) {
            return RuleOutcome.notFired(ID, name());
        }
        String reason = String.format(
                "amount %.2f is %.1fx the card's %d-transaction average of %.2f in the window (threshold %.1fx)",
                amount / 100.0, ratio, snapshot.priorCountInWindow(),
                snapshot.averageAmountInWindow() / 100.0, config.amountFactor());
        return RuleOutcome.fired(ID, name(), config.weight(), reason);
    }
}
