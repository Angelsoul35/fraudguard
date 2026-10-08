package com.angelo.fraudguard.scoring;

import com.angelo.fraudguard.domain.Assessment;
import java.util.Optional;

/**
 * Stores the latest {@link Assessment} per transaction id, so {@code GET
 * /transactions/{id}/assessment} can read the result the scoring subscriber
 * produced asynchronously.
 */
public interface AssessmentStore {

    /** Store (or overwrite) the assessment for its transaction id. */
    void save(Assessment assessment);

    /** Look up the assessment for a transaction id, if one has been produced. */
    Optional<Assessment> find(String transactionId);

    /** Remove all assessments (used by tests). */
    void clear();
}
