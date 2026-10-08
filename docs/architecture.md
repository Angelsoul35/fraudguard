# Architecture

FraudGuard is an event-driven microservice with a deterministic, explainable
scoring core. Ingestion and scoring are decoupled by an in-process event bus;
scoring runs an explainable rule engine over per-card velocity state and stores
an assessment that callers read back by id. Everything is in-memory and
single-process, behind interfaces chosen so each piece can be replaced by a
distributed component without touching the business logic.

## Components

| Component | Package / file | Responsibility |
| --- | --- | --- |
| Domain | `domain/` | `Transaction`, `Assessment`, `RuleOutcome`, `Decision` — immutable records |
| Ingest API | `web/TransactionController` | `POST /transactions`, `GET /transactions/{id}/assessment` |
| Request DTO | `web/dto/TransactionRequest` | JSON body + explicit validation → domain `Transaction` |
| Event bus | `events/EventBus` (+ `InMemoryEventBus`, `KafkaEventBus` stub) | Async publish/subscribe seam |
| Events | `events/DomainEvent`, `TransactionReceived` | Sealed event hierarchy |
| Velocity | `velocity/VelocityStore` (+ `InMemoryVelocityStore`) | Per-card sliding-window state, atomic observe |
| Rules | `rules/Rule` + 5 rule classes + `RuleEngine` | Explainable, independent fraud rules |
| Scoring | `scoring/RiskScorer` | Aggregate contributions → score → decision |
| Subscriber | `scoring/ScoringService` | Consumes events, orchestrates velocity + rules + scoring |
| Assessment store | `scoring/AssessmentStore` (+ `InMemoryAssessmentStore`) | Holds the latest assessment per transaction |
| Geo | `geo/CountryGeo` | Country centroids, haversine distance, high-risk list |
| Config | `config/ScoringProperties`, `VirtualThreadConfig` | Tunable thresholds/weights; virtual-thread executor |
| Errors | `exception/` | `@RestControllerAdvice` → uniform error bodies |

## Data flow

```mermaid
flowchart LR
    CLIENT([client]) -->|POST /transactions| API["TransactionController<br/>validate + publish"]
    API -->|TransactionReceived| BUS{{"EventBus<br/>(in-memory, virtual threads)"}}
    API -.->|202 Accepted| CLIENT
    BUS -->|async deliver| SUB["ScoringService<br/>(subscriber)"]
    SUB -->|observe tx| VEL[("VelocityStore<br/>per-card sliding window")]
    VEL -->|snapshot before tx| ENGINE["RuleEngine"]
    subgraph RULES[five explainable rules]
        ENGINE --> R1["VELOCITY_AMOUNT"]
        ENGINE --> R2["VELOCITY_FREQUENCY"]
        ENGINE --> R3["NEW_COUNTRY"]
        ENGINE --> R4["IMPOSSIBLE_TRAVEL"]
        ENGINE --> R5["ROUND_AMOUNT_HIGH_VALUE"]
    end
    RULES --> SCORE["RiskScorer<br/>sum → clamp 0..100 → decide"]
    SCORE --> STORE[("AssessmentStore")]
    CLIENT -->|GET /transactions/{id}/assessment| STORE
```

## The scoring step, precisely

For each `TransactionReceived`, `ScoringService.score(tx)` runs:

1. `VelocitySnapshot snapshot = velocityStore.observe(tx)` — **atomic per card**:
   returns the card's windowed history *before* `tx` (prior count, average/max
   amount, previous country+time, all-time seen countries) and records `tx` in one
   locked step.
2. `List<RuleOutcome> outcomes = ruleEngine.evaluate(new RuleContext(tx, snapshot))`
   — every rule runs; a throwing rule is logged and treated as not-fired.
3. `int score = riskScorer.score(outcomes)` — sum of fired contributions, clamped
   to `[0, 100]`.
4. `Decision decision = riskScorer.decide(score)` — threshold on the score.
5. `assessmentStore.save(new Assessment(...))` — only the fired rules, each with
   its reason, are kept.

## Why event-driven

Fraud scoring is a streaming workload: decide now, on the back of what just
happened. Request/response would couple the HTTP latency to the scoring latency
and pin the two tiers together. Publishing an event instead lets ingestion return
`202` immediately and lets scoring scale, fail and evolve independently. The
`EventBus` interface keeps the transport swappable: in-memory today, Kafka (keyed
by `cardId`) in production. See `adr/0001`.

## Why virtual threads

Each delivery is a short task; there may be many in flight. Java 21 virtual
threads make "one thread per task" the simplest correct model — no pool sizing,
no queue, and a blocked task costs almost nothing. The bus has its own virtual-
thread executor and the web tier runs on virtual threads too. See `adr/0002`.

## Consistency model

Velocity is order-sensitive, so the read-then-write per card is serialized inside
`VelocityStore.observe` with a per-card lock; different cards proceed
concurrently. The in-memory bus does not guarantee cross-event arrival order,
which is why clients await each assessment before sending the next and why the
Kafka path partitions by `cardId`. See `adr/0004`.

## Scope

In-memory bus, velocity store and assessment store are reference stand-ins. A
real deployment uses Kafka, a shared/bounded feature store, and a learned model
alongside the rules — the rules remaining the explainable floor. See the README's
scope note.
