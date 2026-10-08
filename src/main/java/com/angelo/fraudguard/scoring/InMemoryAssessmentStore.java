package com.angelo.fraudguard.scoring;

import com.angelo.fraudguard.domain.Assessment;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link AssessmentStore} backed by a {@link ConcurrentHashMap}. Fine
 * for a single process and for tests; a real deployment would use a shared
 * store (e.g. Redis or a database) so assessments survive restarts and are
 * visible across instances.
 */
@Component
public class InMemoryAssessmentStore implements AssessmentStore {

    private final ConcurrentHashMap<String, Assessment> assessments = new ConcurrentHashMap<>();

    @Override
    public void save(Assessment assessment) {
        assessments.put(assessment.transactionId(), assessment);
    }

    @Override
    public Optional<Assessment> find(String transactionId) {
        return Optional.ofNullable(assessments.get(transactionId));
    }

    @Override
    public void clear() {
        assessments.clear();
    }
}
