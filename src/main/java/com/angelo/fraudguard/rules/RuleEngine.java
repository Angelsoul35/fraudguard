package com.angelo.fraudguard.rules;

import com.angelo.fraudguard.domain.RuleOutcome;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs every registered {@link Rule} against a {@link RuleContext} and returns
 * their outcomes.
 *
 * <p>Spring injects the full list of rule beans, so adding a rule is as simple
 * as adding a {@code @Component} that implements {@link Rule} — no registration
 * list to update here. The engine returns all outcomes (fired and not); callers
 * decide what to keep. It never throws on a single rule's failure: a misbehaving
 * rule is logged and skipped so it cannot take down scoring.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Evaluate all rules; returns one outcome per rule, in rule order. */
    public List<RuleOutcome> evaluate(RuleContext context) {
        return rules.stream()
                .map(rule -> safeEvaluate(rule, context))
                .toList();
    }

    /** The rules this engine will run (for diagnostics and documentation). */
    public List<Rule> rules() {
        return rules;
    }

    private RuleOutcome safeEvaluate(Rule rule, RuleContext context) {
        try {
            return rule.evaluate(context);
        } catch (RuntimeException ex) {
            log.error("Rule {} threw; treating as not-fired: {}", rule.id(), ex.getMessage(), ex);
            return RuleOutcome.notFired(rule.id(), rule.name());
        }
    }
}
