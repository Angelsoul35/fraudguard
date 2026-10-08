package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * Round-amount + high-value rule.
 *
 * <p>Legitimate purchases rarely land on a suspiciously round, large figure;
 * fraudulent cash-outs often do (a thief entering "5000", not "4987.63"). This
 * rule fires when the amount is both at or above {@code roundAmount.minValueCents}
 * and an exact multiple of 100 major currency units (i.e. no cents and a whole
 * hundred). On its own it is weak evidence — hence a small weight — but it adds
 * meaningfully on top of a velocity or location signal.
 */
@Component
public class RoundAmountHighValueRule implements Rule {

    public static final String ID = "ROUND_AMOUNT_HIGH_VALUE";

    /** 100 major units expressed in cents (100 * 100). */
    private static final long ROUND_UNIT_CENTS = 10_000L;

    private final ScoringProperties.RoundAmount config;

    public RoundAmountHighValueRule(ScoringProperties properties) {
        this.config = properties.roundAmount();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Round amount at high value";
    }

    @Override
    public RuleOutcome evaluate(RuleContext context) {
        long amount = context.transaction().amount();
        boolean highValue = amount >= config.minValueCents();
        boolean round = amount > 0 && amount % ROUND_UNIT_CENTS == 0;
        if (!highValue || !round) {
            return RuleOutcome.notFired(ID, name());
        }
        String reason = String.format(
                "round high-value amount %.2f (exact multiple of 100.00, at or above %.2f)",
                amount / 100.0, config.minValueCents() / 100.0);
        return RuleOutcome.fired(ID, name(), config.weight(), reason);
    }
}
