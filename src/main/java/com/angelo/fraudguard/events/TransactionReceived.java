package com.angelo.fraudguard.events;

import com.angelo.fraudguard.domain.Transaction;
import java.time.Instant;

/**
 * Published when a transaction has been ingested at {@code POST /transactions}
 * and is ready to be scored. The scoring subscriber consumes it off the bus.
 *
 * @param transaction the ingested transaction
 * @param occurredAt  when the event was published
 */
public record TransactionReceived(Transaction transaction, Instant occurredAt) implements DomainEvent {

    /** Create the event stamped with the current instant. */
    public static TransactionReceived of(Transaction transaction) {
        return new TransactionReceived(transaction, Instant.now());
    }
}
