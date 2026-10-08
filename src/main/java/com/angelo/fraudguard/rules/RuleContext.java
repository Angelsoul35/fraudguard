package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.velocity.VelocitySnapshot;

/**
 * Everything a rule needs to make its decision: the transaction being scored and
 * the card's velocity snapshot as it was <em>before</em> this transaction.
 *
 * @param transaction the transaction under evaluation
 * @param snapshot    the card's pre-transaction velocity snapshot
 */
public record RuleContext(Transaction transaction, VelocitySnapshot snapshot) {
}
