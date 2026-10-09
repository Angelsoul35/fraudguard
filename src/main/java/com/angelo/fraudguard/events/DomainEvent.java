package com.angelo.fraudguard.events;

import java.time.Instant;

/**
 * Marker for everything that travels over the {@link EventBus}.
 *
 * <p>It is a sealed interface: the set of event types is closed and known at
 * compile time, which forces every subscriber to account for each event type and
 * lets a {@code switch} over events be exhaustive. Today there is a single event;
 * new ones are added here.
 */
public sealed interface DomainEvent permits TransactionReceived {

    /** When the event was created (publish time). */
    Instant occurredAt();
}
