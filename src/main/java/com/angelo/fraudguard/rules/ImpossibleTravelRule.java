package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.RuleOutcome;
import com.angelo.fraudguard.geo.CountryGeo;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Impossible-travel rule.
 *
 * <p>Catches the same card being used in two distant countries closer together
 * in time than any traveller could manage — a strong signal that the card (or
 * its details) is in two places at once. It computes the great-circle distance
 * between the previous and current transaction's countries and the implied speed
 * over the elapsed time; it fires when that speed exceeds {@code travel.maxSpeedKmh}
 * (900 km/h by default, roughly a jet airliner). Two transactions in different
 * countries at the same instant are treated as infinite speed and fire outright.
 */
@Component
public class ImpossibleTravelRule implements Rule {

    public static final String ID = "IMPOSSIBLE_TRAVEL";

    private final ScoringProperties.Travel config;

    public ImpossibleTravelRule(ScoringProperties properties) {
        this.config = properties.travel();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Impossible travel between countries";
    }

    @Override
    public RuleOutcome evaluate(RuleContext context) {
        var snapshot = context.snapshot();
        if (snapshot.previousCountry().isEmpty() || snapshot.previousTimestamp().isEmpty()) {
            return RuleOutcome.notFired(ID, name());
        }
        String previous = snapshot.previousCountry().get();
        String current = context.transaction().country();
        if (previous.equals(current)) {
            return RuleOutcome.notFired(ID, name());
        }
        Optional<Double> distance = CountryGeo.distanceKm(previous, current);
        if (distance.isEmpty() || distance.get() <= 0) {
            return RuleOutcome.notFired(ID, name());
        }
        double distanceKm = distance.get();

        Duration elapsed = Duration.between(snapshot.previousTimestamp().get(), context.transaction().timestamp());
        long seconds = elapsed.toSeconds();

        if (seconds <= 0) {
            String reason = String.format(
                    "card used in %s then %s (~%.0f km apart) at effectively the same time",
                    previous, current, distanceKm);
            return RuleOutcome.fired(ID, name(), config.weight(), reason);
        }
        double hours = seconds / 3600.0;
        double speedKmh = distanceKm / hours;
        if (speedKmh <= config.maxSpeedKmh()) {
            return RuleOutcome.notFired(ID, name());
        }
        String reason = String.format(
                "card used in %s then %s (~%.0f km) within %d min implies %.0f km/h (limit %.0f km/h)",
                previous, current, distanceKm, Math.max(1, seconds / 60), speedKmh, config.maxSpeedKmh());
        return RuleOutcome.fired(ID, name(), config.weight(), reason);
    }
}
