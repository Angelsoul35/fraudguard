# ADR 0004 — In-memory per-card velocity state, and its limits

## Status
Accepted.

## Context
The velocity rules need per-card history: how many transactions in the last N
minutes, their average amount, the previous transaction's country and time, and
the set of countries the card has ever used. This state is read and written on
every transaction, and it is **order-sensitive** — a rule must see the state that
preceded the event it is scoring, not after. The service must also run with no
database so the demo and tests are self-contained.

## Decision
Keep velocity state in memory in `InMemoryVelocityStore`, behind a `VelocityStore`
interface, with a single atomic operation:

```java
VelocitySnapshot observe(Transaction tx);
```

Per card it holds a time-ordered deque pruned to the configured window using
**event time** (the transaction's own timestamp, not wall-clock), the immediately
preceding transaction (kept even after it leaves the window, so impossible-travel
always has a reference), and an all-time set of seen countries. `observe` takes a
**per-card `ReentrantLock`** for the whole read-then-write: it computes the
pre-transaction snapshot and records the transaction in one guarded step.

## Consequences
- The read-evaluate-record sequence is consistent for a given card — two
  concurrent events for one card cannot interleave their snapshot and update —
  while different cards are scored fully concurrently (separate locks, a
  `ConcurrentHashMap` of card states).
- Using event time for the window means a replayed or slightly out-of-order batch
  is judged by the transactions' own timestamps.
- The limits are real and deliberate:
  - **Process-local.** State is not shared across instances and does not survive a
    restart. Horizontal scaling would split a card's history across nodes.
  - **Unbounded.** Cards and their seen-country sets accumulate with no eviction;
    fine for a demo, not for production memory.
  - **Arrival order is not guaranteed by the in-memory bus.** The lock gives
    atomicity, not ordering. The demo and tests await each assessment before
    sending the next to keep per-card order deterministic.
- Production replaces this with a shared, bounded **feature store** (e.g. Redis
  with TTLs, or a streaming aggregator), and gets per-card ordering from Kafka
  partitioning by `cardId` (ADR 0001) rather than from client pacing.
