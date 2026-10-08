package com.angelo.fraudguard.events;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * An in-process {@link EventBus} that delivers each event to each subscriber on
 * its own Java 21 virtual thread.
 *
 * <p>Handlers are held per concrete event type. {@link #publish} looks up the
 * handlers for the event's exact class and submits one task per handler to the
 * virtual-thread executor, so publishing never blocks on scoring and a slow
 * handler cannot hold up the caller or the other handlers.
 *
 * <p>A handler that throws is logged and isolated: one failing subscriber does
 * not stop the others from receiving the event. This is single-process and
 * at-most-once with no persistence — the trade-offs, and the Kafka path that
 * fixes them, are described in {@code docs/adr/0001}.
 */
@Component
public class InMemoryEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventBus.class);

    private final ExecutorService executor;
    private final ConcurrentHashMap<Class<? extends DomainEvent>, List<Consumer<? extends DomainEvent>>> handlers =
            new ConcurrentHashMap<>();

    public InMemoryEventBus(@Qualifier("eventBusExecutor") ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public <E extends DomainEvent> void subscribe(Class<E> eventType, Consumer<E> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.debug("Subscribed handler for {}", eventType.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void publish(DomainEvent event) {
        List<Consumer<? extends DomainEvent>> forType = handlers.get(event.getClass());
        if (forType == null || forType.isEmpty()) {
            log.debug("No subscribers for {}", event.getClass().getSimpleName());
            return;
        }
        for (Consumer<? extends DomainEvent> raw : forType) {
            Consumer<DomainEvent> handler = (Consumer<DomainEvent>) raw;
            executor.execute(() -> {
                try {
                    handler.accept(event);
                } catch (RuntimeException ex) {
                    // Isolate a failing subscriber: log and keep the others alive.
                    log.error("Subscriber failed handling {}: {}",
                            event.getClass().getSimpleName(), ex.getMessage(), ex);
                }
            });
        }
    }
}
