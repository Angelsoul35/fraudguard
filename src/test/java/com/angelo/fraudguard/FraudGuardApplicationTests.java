package com.angelo.fraudguard;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.events.EventBus;
import com.angelo.fraudguard.rules.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FraudGuardApplicationTests {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private RuleEngine ruleEngine;

    @Test
    void context_loads_and_wires_all_five_rules() {
        assertThat(eventBus).isNotNull();
        // All five explainable rules are discovered and registered.
        assertThat(ruleEngine.rules()).hasSize(5);
    }
}
