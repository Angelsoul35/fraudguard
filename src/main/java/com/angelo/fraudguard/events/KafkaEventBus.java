package com.angelo.fraudguard.events;

import java.util.function.Consumer;

/**
 * STUB — a sketch of a Kafka-backed {@link EventBus}, not wired into the
 * application. It exists to show that the broker is an implementation detail
 * behind the {@link EventBus} seam: swapping transports is a configuration
 * change, not a rewrite of the controller or the scoring service.
 *
 * <p>It is intentionally never registered as a Spring bean (no
 * {@code @Component}), so the shipped service runs entirely in-process and the
 * tests need no broker. A real implementation would:
 *
 * <ul>
 *   <li>serialize each {@link DomainEvent} and {@code producer.send(...)} it to a
 *       topic, <strong>keyed by {@code cardId}</strong> so that all events for a
 *       card land on one partition and are therefore consumed in order — the
 *       per-card ordering the velocity features depend on;</li>
 *   <li>run a consumer group per subscriber type, committing offsets only after a
 *       successful handle, which upgrades delivery from the in-memory bus's
 *       at-most-once to at-least-once (so handlers must be idempotent);</li>
 *   <li>let Kafka provide the durability, replay and horizontal scale the
 *       in-memory bus cannot.</li>
 * </ul>
 *
 * See {@code docs/adr/0001} for the full rationale.
 */
public final class KafkaEventBus implements EventBus {

    private static final String UNIMPLEMENTED =
            "KafkaEventBus is a documentation stub. Wire a KafkaTemplate/consumer here, "
                    + "keying records by cardId for per-card ordering, to run FraudGuard on Kafka.";

    @Override
    public <E extends DomainEvent> void subscribe(Class<E> eventType, Consumer<E> handler) {
        throw new UnsupportedOperationException(UNIMPLEMENTED);
    }

    @Override
    public void publish(DomainEvent event) {
        throw new UnsupportedOperationException(UNIMPLEMENTED);
    }
}
