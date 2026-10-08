package com.angelo.fraudguard.velocity;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * A read-only view of a card's history <em>just before</em> the current
 * transaction, computed over the configured sliding window (plus a little
 * all-time state). Rules consume this snapshot; they never touch the mutable
 * store directly.
 *
 * @param priorCountInWindow number of prior transactions for this card within the window
 * @param averageAmountInWindow mean amount (cents) of those prior transactions, 0 if none
 * @param maxAmountInWindow largest prior amount (cents) within the window, 0 if none
 * @param previousCountry the country of the immediately preceding transaction, if any
 * @param previousTimestamp the event time of the immediately preceding transaction, if any
 * @param seenCountries the set of countries this card has ever transacted in before now
 */
public record VelocitySnapshot(
        int priorCountInWindow,
        double averageAmountInWindow,
        long maxAmountInWindow,
        Optional<String> previousCountry,
        Optional<Instant> previousTimestamp,
        Set<String> seenCountries) {

    public VelocitySnapshot {
        seenCountries = Set.copyOf(seenCountries);
    }

    /** True when this card has no prior history at all (a first-seen card). */
    public boolean isFirstSeen() {
        return priorCountInWindow == 0 && previousTimestamp.isEmpty();
    }
}
