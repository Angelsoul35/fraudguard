package com.angelo.fraudguard.events;

import java.util.function.Consumer;

/**
 * A minimal publish/subscribe bus: the seam between ingestion and scoring.
 *
 * <p>Ingestion publishes a {@link DomainEvent} and returns immediately; one or
 * more subscribers consume it asynchronously. The controller therefore never
 * blocks on scoring, and scoring can be scaled or replaced independently.
 *
 * <p>The interface is intentionally broker-agnostic. The shipped implementation
 * is {@link InMemoryEventBus} (virtual-thread delivery, single process); a
 * Kafka-backed implementation ({@link KafkaEventBus}) would drop in behind this
 * same interface without touching the controller or the scoring service. See
 * {@code docs/adr/0001}.
 */
public interface EventBus {

    /**
     * Register a handler for a given event type. Handlers for the same type are
     * invoked in registration order, each on its own delivery thread.
     *
     * @param eventType the concrete event class to listen for
     * @param handler   the consumer invoked for each matching event
     * @param <E>       the event type
     */
    <E extends DomainEvent> void subscribe(Class<E> eventType, Consumer<E> handler);

    /**
     * Publish an event to all matching subscribers. Delivery is asynchronous:
     * this call returns without waiting for subscribers to finish.
     *
     * @param event the event to publish
     */
    void publish(DomainEvent event);
}
