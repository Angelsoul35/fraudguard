package com.angelo.fraudguard.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.Transaction;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryVelocityStoreTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryVelocityStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVelocityStore(ScoringProperties.defaults());
    }

    private static Transaction tx(String card, long amount, String country, Instant ts) {
        return new Transaction("t-" + ts, card, amount, "USD", "Shop", country, ts);
    }

    @Test
    void first_observation_of_a_card_has_no_history() {
        VelocitySnapshot snapshot = store.observe(tx("card-1", 1_000, "US", T0));

        assertThat(snapshot.isFirstSeen()).isTrue();
        assertThat(snapshot.priorCountInWindow()).isZero();
        assertThat(snapshot.previousCountry()).isEmpty();
        assertThat(snapshot.seenCountries()).isEmpty();
    }

    @Test
    void snapshot_reflects_the_state_before_the_current_transaction() {
        store.observe(tx("card-1", 1_000, "US", T0));
        VelocitySnapshot snapshot = store.observe(tx("card-1", 3_000, "CA", T0.plus(Duration.ofMinutes(1))));

        // The snapshot is pre-transaction: it sees the first charge, not the second.
        assertThat(snapshot.priorCountInWindow()).isEqualTo(1);
        assertThat(snapshot.averageAmountInWindow()).isEqualTo(1_000.0);
        assertThat(snapshot.previousCountry()).contains("US");
        assertThat(snapshot.previousTimestamp()).contains(T0);
        assertThat(snapshot.seenCountries()).containsExactly("US");
    }

    @Test
    void average_and_max_are_computed_over_prior_in_window_transactions() {
        store.observe(tx("card-1", 1_000, "US", T0));
        store.observe(tx("card-1", 3_000, "US", T0.plus(Duration.ofMinutes(1))));
        VelocitySnapshot snapshot = store.observe(tx("card-1", 9_000, "US", T0.plus(Duration.ofMinutes(2))));

        assertThat(snapshot.priorCountInWindow()).isEqualTo(2);
        assertThat(snapshot.averageAmountInWindow()).isEqualTo(2_000.0);
        assertThat(snapshot.maxAmountInWindow()).isEqualTo(3_000);
    }

    @Test
    void transactions_older_than_the_window_are_pruned_from_counts() {
        store.observe(tx("card-1", 1_000, "US", T0));
        // 20 minutes later, well outside the 10-minute window.
        VelocitySnapshot snapshot = store.observe(tx("card-1", 1_000, "US", T0.plus(Duration.ofMinutes(20))));

        assertThat(snapshot.priorCountInWindow()).isZero();
        // ...but the previous transaction is still remembered for impossible-travel,
        // and the country is still in the all-time seen set.
        assertThat(snapshot.previousTimestamp()).contains(T0);
        assertThat(snapshot.seenCountries()).containsExactly("US");
    }

    @Test
    void counts_reflect_the_sliding_window_as_it_moves() {
        // Six transactions one minute apart: all within a 10-minute window.
        for (int i = 0; i < 6; i++) {
            store.observe(tx("card-1", 1_000, "US", T0.plus(Duration.ofMinutes(i))));
        }
        VelocitySnapshot snapshot = store.observe(tx("card-1", 1_000, "US", T0.plus(Duration.ofMinutes(6))));

        assertThat(snapshot.priorCountInWindow()).isEqualTo(6);
    }

    @Test
    void different_cards_keep_independent_state() {
        store.observe(tx("card-1", 1_000, "US", T0));
        VelocitySnapshot other = store.observe(tx("card-2", 1_000, "FR", T0.plus(Duration.ofMinutes(1))));

        assertThat(other.isFirstSeen()).isTrue();
        assertThat(other.seenCountries()).isEmpty();
    }
}
