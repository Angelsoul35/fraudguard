package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.geo.CountryGeo;
import org.springframework.stereotype.Component;

/**
 * New / high-risk country rule.
 *
 * <p>Catches a card suddenly transacting somewhere it never has before, and
 * transactions in countries flagged as elevated risk. A high-risk country fires
 * regardless of history (and carries the larger weight); a country simply new to
 * a card with prior history fires with the base weight. A brand-new card's first
 * transaction does not fire on novelty alone — there is no history for the
 * country to be "new" relative to — unless that country is high-risk.
 */
@Component
public class NewCountryRule implements Rule {

    public static final String ID = "NEW_COUNTRY";

    private final ScoringProperties.NewCountry config;

    public NewCountryRule(ScoringProperties properties) {
        this.config = properties.newCountry();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "New or high-risk country for the card";
    }

    @Override
    public RuleOutcome evaluate(RuleContext context) {
        String country = context.transaction().country();
        var snapshot = context.snapshot();
        boolean highRisk = CountryGeo.isHighRisk(country);
        boolean isNew = !snapshot.seenCountries().isEmpty() && !snapshot.seenCountries().contains(country);

        if (highRisk) {
            String reason = isNew
                    ? String.format("transaction in high-risk country %s, also new for this card", country)
                    : String.format("transaction in high-risk country %s", country);
            return RuleOutcome.fired(ID, name(), config.highRiskWeight(), reason);
        }
        if (isNew) {
            String reason = String.format(
                    "first transaction for this card in %s (previously seen in %s)",
                    country, snapshot.seenCountries());
            return RuleOutcome.fired(ID, name(), config.weight(), reason);
        }
        return RuleOutcome.notFired(ID, name());
    }
}
