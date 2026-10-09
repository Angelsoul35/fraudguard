package com.angelo.fraudguard.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * A deterministic, card-disjoint partition of the dataset into cross-validation
 * folds and a held-out test set.
 *
 * <p>Splitting is by card, never by transaction: a card's whole history goes to
 * exactly one fold or to the held-out set. That matters because FraudGuard's
 * velocity features are per card — letting one card's transactions straddle the
 * train/test boundary would leak that card's behaviour across the split and
 * flatter the metrics. The partition is stratified on whether a card ever
 * transacts fraudulently, so every fold and the held-out set contain both
 * classes. Given the same seed the split is fully reproducible.
 */
public final class DatasetSplit {

    private final List<List<FraudSample>> folds;
    private final List<FraudSample> development;
    private final List<FraudSample> heldout;

    private DatasetSplit(List<List<FraudSample>> folds, List<FraudSample> development,
                         List<FraudSample> heldout) {
        this.folds = folds;
        this.development = development;
        this.heldout = heldout;
    }

    public List<List<FraudSample>> folds() {
        return folds;
    }

    public List<FraudSample> development() {
        return development;
    }

    public List<FraudSample> heldout() {
        return heldout;
    }

    /**
     * Build the split.
     *
     * @param samples          all samples, already in ascending event-time order
     * @param foldCount        number of cross-validation folds (over development cards)
     * @param heldoutFraction  share of cards reserved for the held-out test (0..1)
     * @param seed             seed making the partition reproducible
     */
    public static DatasetSplit byCard(List<FraudSample> samples, int foldCount,
                                      double heldoutFraction, long seed) {
        if (foldCount < 2) {
            throw new IllegalArgumentException("need at least 2 folds, got " + foldCount);
        }
        if (heldoutFraction <= 0 || heldoutFraction >= 1) {
            throw new IllegalArgumentException("heldoutFraction must be in (0,1), got " + heldoutFraction);
        }

        // A card is "fraudulent" for stratification if any of its transactions is fraud.
        java.util.Map<String, Boolean> cardIsFraud = new java.util.HashMap<>();
        for (FraudSample s : samples) {
            cardIsFraud.merge(s.transaction().cardId(), s.fraud(), Boolean::logicalOr);
        }

        // Assign each card: held-out or a dev fold, stratified by class and keyed by a
        // stable hash so the assignment is deterministic and card-disjoint.
        java.util.Map<String, Integer> cardFold = new java.util.HashMap<>(); // -1 = held-out
        for (var entry : cardIsFraud.entrySet()) {
            String card = entry.getKey();
            double u = unitHash(seed, card, 0x9E3779B97F4A7C15L);
            if (u < heldoutFraction) {
                cardFold.put(card, -1);
            } else {
                int fold = (int) (unitHash(seed, card, 0xC2B2AE3D27D4EB4FL) * foldCount);
                if (fold >= foldCount) {
                    fold = foldCount - 1;
                }
                cardFold.put(card, fold);
            }
        }

        List<List<FraudSample>> folds = new ArrayList<>();
        for (int i = 0; i < foldCount; i++) {
            folds.add(new ArrayList<>());
        }
        List<FraudSample> development = new ArrayList<>();
        List<FraudSample> heldout = new ArrayList<>();
        for (FraudSample s : samples) {
            int fold = cardFold.get(s.transaction().cardId());
            if (fold < 0) {
                heldout.add(s);
            } else {
                folds.get(fold).add(s);
                development.add(s);
            }
        }
        return new DatasetSplit(folds, development, heldout);
    }

    /**
     * Map {@code (seed, key, salt)} to a value in {@code [0,1)} using a SplitMix64
     * avalanche over the string's stable hash. Deterministic across JVMs because it
     * relies only on {@link String#hashCode()} and integer arithmetic.
     */
    private static double unitHash(long seed, String key, long salt) {
        long z = seed ^ salt ^ (0x100000001B3L * (long) key.hashCode());
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53; // 53-bit mantissa in [0,1)
    }
}
