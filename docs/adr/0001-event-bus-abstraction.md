# ADR 0001 — An event bus abstraction between ingestion and scoring

## Status
Accepted.

## Context
Fraud scoring is a streaming problem: a transaction must be scored on the back of
what the card did seconds earlier, and the HTTP caller should not wait on that
work. We also want the demo and the whole test suite to run with no external
infrastructure — no broker, no database — while keeping a clear, honest path to a
production streaming platform.

If the controller called the scorer directly, the HTTP latency would include the
scoring latency, the two tiers could not scale independently, and moving to a
broker later would mean editing the controller and the scorer.

## Decision
Put a small `EventBus` interface between ingestion and scoring:

```java
<E extends DomainEvent> void subscribe(Class<E> type, Consumer<E> handler);
void publish(DomainEvent event);
```

The controller depends only on `publish`; the scoring subscriber only on
`subscribe`. The shipped implementation, `InMemoryEventBus`, delivers each event
to each subscriber asynchronously on a virtual thread (see ADR 0002), isolating a
slow or failing subscriber. `KafkaEventBus` is included as a **documented stub**
behind the same interface — never registered as a bean — to make the drop-in
concrete.

## Consequences
- Ingestion returns `202 Accepted` without blocking on scoring; the tiers are
  decoupled.
- Tests and the local run need no broker: the in-memory bus is the default and
  the only wired implementation.
- The trade-off is explicit. The in-memory bus is **single-process**,
  **at-most-once**, and loses events on crash. A Kafka implementation fixes all
  three: publish records **keyed by `cardId`** so a card's events share a
  partition and are consumed in order (the per-card ordering velocity depends on,
  see ADR 0004), run a consumer group per subscriber committing offsets after a
  successful handle (at-least-once, so handlers must be idempotent), and let Kafka
  provide durability, replay and horizontal scale.
- Because the interface is transport-agnostic, that change is an implementation
  swap and some configuration — not a change to the controller or the scorer.
