package com.angelo.fraudguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FraudGuard — an event-driven, real-time transaction fraud-scoring
 * microservice.
 *
 * <p>Ingested transactions are published to an in-process event bus; a scoring
 * subscriber runs an explainable rule engine over per-card velocity state and
 * stores a risk assessment that callers read back by id. See {@code README.md}
 * and {@code docs/architecture.md}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudGuardApplication.class, args);
    }
}
