package com.angelo.fraudguard.velocity;

import com.angelo.fraudguard.domain.Transaction;

/**
 * Per-card velocity state over a sliding time window.
 *
 * <p>The single operation {@link #observe(Transaction)} is deliberately atomic
 * per card: it returns the snapshot of the card's history <em>before</em> this
 * transaction and records the transaction in one guarded step. That ordering
 * matters — a rule must see the state that preceded the event it is scoring, and
 * two concurrent events for the same card must not interleave their
 * read-then-write. See {@code docs/adr/0004} for the consistency model and its
 * limits.
 */
public interface VelocityStore {

    /**
     * Atomically compute the pre-transaction snapshot for this card and record
     * the transaction into the window.
     *
     * @param tx the transaction being scored
     * @return the card's velocity snapshot as it was immediately before {@code tx}
     */
    VelocitySnapshot observe(Transaction tx);

    /** Remove all state (used by tests). */
    void clear();
}
