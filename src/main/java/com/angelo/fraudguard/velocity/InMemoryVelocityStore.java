package com.angelo.fraudguard.velocity;

import com.angelo.fraudguard.config.ScoringProperties;
import com.angelo.fraudguard.domain.Transaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * In-memory, sliding-window velocity store.
 *
 * <p>For each card it keeps a time-ordered deque of recent transactions (pruned
 * to the configured window using <em>event time</em>, not wall-clock), the
 * immediately preceding transaction (kept even after it falls out of the window,
 * so the impossible-travel rule always has a reference point), and the set of
 * countries the card has ever been seen in.
 *
 * <p>Each card has its own {@link ReentrantLock}; {@link #observe} takes that
 * lock for the whole read-then-write so updates for a single card are
 * serialized, while different cards proceed concurrently. This is the
 * consistency contract the rule engine relies on. Its limits (unbounded growth,
 * no persistence, process-local) are documented in {@code docs/adr/0004}.
 */
@Component
public class InMemoryVelocityStore implements VelocityStore {

    private record Entry(Instant timestamp, long amount, String country) {
    }

    private static final class CardState {
        final ReentrantLock lock = new ReentrantLock();
        final Deque<Entry> window = new ArrayDeque<>();
        final Set<String> seenCountries = new HashSet<>();
        String previousCountry;
        Instant previousTimestamp;
    }

    private final ConcurrentHashMap<String, CardState> states = new ConcurrentHashMap<>();
    private final Duration window;

    public InMemoryVelocityStore(ScoringProperties properties) {
        this.window = properties.window();
    }

    @Override
    public VelocitySnapshot observe(Transaction tx) {
        CardState state = states.computeIfAbsent(tx.cardId(), k -> new CardState());
        state.lock.lock();
        try {
            // Drop entries older than (tx.timestamp - window): the window is relative
            // to the event being scored, so out-of-window history does not inflate it.
            Instant cutoff = tx.timestamp().minus(window);
            state.window.removeIf(e -> !e.timestamp().isAfter(cutoff));

            int count = state.window.size();
            long sum = 0;
            long max = 0;
            for (Entry e : state.window) {
                sum += e.amount();
                max = Math.max(max, e.amount());
            }
            double average = count == 0 ? 0.0 : (double) sum / count;

            VelocitySnapshot snapshot = new VelocitySnapshot(
                    count,
                    average,
                    max,
                    Optional.ofNullable(state.previousCountry),
                    Optional.ofNullable(state.previousTimestamp),
                    new LinkedHashSet<>(state.seenCountries));

            // Record this transaction into the card's state.
            state.window.addLast(new Entry(tx.timestamp(), tx.amount(), tx.country()));
            state.previousCountry = tx.country();
            state.previousTimestamp = tx.timestamp();
            if (tx.country() != null) {
                state.seenCountries.add(tx.country());
            }
            return snapshot;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public void clear() {
        states.clear();
    }
}
