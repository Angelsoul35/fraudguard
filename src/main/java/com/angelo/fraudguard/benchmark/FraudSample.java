package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.domain.Transaction;

/**
 * One labelled row from the real card-transaction benchmark: the
 * {@link Transaction} fed to FraudGuard's scorer, paired with the ground-truth
 * {@code fraud} label used only for evaluation (never for scoring).
 *
 * @param transaction the transaction presented to the scorer
 * @param fraud       whether this transaction was labelled fraudulent in the dataset
 */
public record FraudSample(Transaction transaction, boolean fraud) {
}
