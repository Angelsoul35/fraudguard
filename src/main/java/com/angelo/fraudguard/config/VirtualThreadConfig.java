package com.angelo.fraudguard.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the virtual-thread executor used by the in-memory event bus.
 *
 * <p>Spring Boot already runs the web tier on virtual threads via
 * {@code spring.threads.virtual.enabled=true}. This bean gives the event bus its
 * own dedicated executor, also backed by Java 21 virtual threads: every event
 * delivery runs on a fresh virtual thread, so a slow subscriber blocks only its
 * own lightweight thread and never starves a shared pool. Because virtual
 * threads are cheap, there is no fixed pool size to tune and no queue to
 * overflow. See {@code docs/adr/0002}.
 */
@Configuration
public class VirtualThreadConfig {

    /**
     * An unbounded, per-task virtual-thread executor for event delivery.
     * Named so it can be injected explicitly and shut down with the context.
     */
    @Bean(name = "eventBusExecutor", destroyMethod = "close")
    public ExecutorService eventBusExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("fraudguard-eventbus-", 0).factory());
    }
}
