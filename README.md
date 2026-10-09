# FraudGuard

Real-time, **event-driven** fraud scoring for payment-card transactions. A
transaction is ingested over HTTP, published onto an in-process **event bus**,
and scored asynchronously by a subscriber that runs an **explainable rule
engine** over per-card **velocity** state. The result is a risk score (0–100), a
decision (`APPROVE` / `REVIEW` / `DECLINE`), and the exact list of rules that
fired with a human reason for each — so every decision can be audited, not just
trusted.

It runs entirely in one process with no external services: the bus, the velocity
store and the assessment store are all in-memory, and the whole test suite passes
offline with no broker and no database. The seams are deliberate — the
`EventBus` interface has a documented Kafka implementation path — so the same
code grows into a distributed pipeline without a rewrite.

[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue)](.github/workflows/ci.yml)
[![Java](https://img.shields.io/badge/java-21-blue)](pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](pom.xml)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

---

## Why it matters

Card fraud is a streaming problem: decisions must land in milliseconds, each on
the back of what the card did seconds ago. Two design choices follow from that
and shape the whole service:

- **Event-driven, not request/response.** Ingestion publishes an event and
  returns `202 Accepted` immediately; scoring happens off the request thread.
  The HTTP tier never blocks on scoring, scoring can be scaled or replaced
  independently, and swapping the in-memory bus for Kafka is a change behind one
  interface — not a rewrite of the controller.
- **Rules first, and explainable.** A card issuer has to be able to say *why* a
  charge was declined — to a regulator, to an analyst, to the cardholder. Every
  rule here is a pure, named function that returns a score contribution **and a
  reason**. The score is their sum; the decision is a threshold on the score.
  Nothing is a black box. A learned model belongs *alongside* these rules as one
  more signal (see [scope](#a-note-on-scope)), never as an unexplained verdict.

## How it works

```mermaid
flowchart LR
    CLIENT([client]) -->|POST /transactions| API["TransactionController<br/>validate + publish"]
    API -->|TransactionReceived| BUS{{"EventBus<br/>(in-memory, virtual threads)"}}
    API -.->|202 Accepted| CLIENT
    BUS -->|async deliver| SUB["ScoringService<br/>(subscriber)"]
    SUB --> VEL[("VelocityStore<br/>per-card sliding window")]
    VEL -->|snapshot| ENGINE["RuleEngine<br/>5 explainable rules"]
    ENGINE --> SCORE["RiskScorer<br/>sum -> 0..100 -> decision"]
    SCORE --> STORE[("AssessmentStore")]
    CLIENT -->|GET /transactions/{id}/assessment| STORE
```

1. **Ingest** (`web/TransactionController`). `POST /transactions` validates the
   body, builds a domain `Transaction` (amount in integer **cents**), publishes a
   `TransactionReceived` event, and returns `202 Accepted` with the URL to poll.
2. **Event bus** (`events/`). `InMemoryEventBus` delivers each event to each
   subscriber on its own Java 21 **virtual thread**. A slow or failing subscriber
   is isolated and never blocks the publisher. `KafkaEventBus` is a documented
   stub behind the same `EventBus` interface.
3. **Score** (`scoring/ScoringService`). The subscriber calls
   `VelocityStore.observe(tx)` — one atomic, per-card step that returns the card's
   snapshot *before* this transaction and records it — then runs the
   `RuleEngine`, aggregates with `RiskScorer`, and stores the `Assessment`.
4. **Expose** (`GET /transactions/{id}/assessment`). Returns the score, decision
   and fired rules, or `404` if the id is unknown or not yet scored.

## Quick start

Requires **Java 21** and **Maven**.

```bash
mvn test            # full suite, runs offline (no broker, no DB)
mvn spring-boot:run # starts on http://localhost:8080
# (port taken? mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8099)
```

### Score a clean transaction → `APPROVE`

```bash
curl -s -X POST localhost:8080/transactions -H 'content-type: application/json' -d '{
  "id":"clean-001","cardId":"card-9001","amount":4599,"currency":"USD",
  "merchant":"Corner Coffee","country":"US","timestamp":"2026-10-08T12:00:00Z"}'

curl -s localhost:8080/transactions/clean-001/assessment
# {"score":0,"decision":"APPROVE","firedRules":[], ...}
```

### Score a fraud-looking sequence → `DECLINE`

A burst of charges on one card, then a charge in Japan two minutes after the last
US charge — a velocity burst *and* impossible travel *and* a new country at once:

```bash
for i in 0 1 2 3 4 5; do
  curl -s -X POST localhost:8080/transactions -H 'content-type: application/json' -d "{
    \"id\":\"burst-$i\",\"cardId\":\"card-6666\",\"amount\":2000,\"currency\":\"USD\",
    \"merchant\":\"Gas N Go\",\"country\":\"US\",\"timestamp\":\"2026-10-08T12:0$i:00Z\"}"
done
curl -s -X POST localhost:8080/transactions -H 'content-type: application/json' -d '{
  "id":"burst-final","cardId":"card-6666","amount":2000,"currency":"USD",
  "merchant":"Tokyo Electronics","country":"JP","timestamp":"2026-10-08T12:07:00Z"}'

curl -s localhost:8080/transactions/burst-final/assessment
```

```json
{
  "transactionId": "burst-final", "cardId": "card-6666",
  "score": 85, "decision": "DECLINE",
  "firedRules": [
    {"ruleId": "IMPOSSIBLE_TRAVEL", "contribution": 40,
     "reason": "card used in US then JP (~9756 km) within 2 min implies 292678 km/h (limit 900 km/h)"},
    {"ruleId": "NEW_COUNTRY", "contribution": 20,
     "reason": "first transaction for this card in JP (previously seen in [US])"},
    {"ruleId": "VELOCITY_FREQUENCY", "contribution": 25,
     "reason": "this is transaction 7 for the card within 10 minutes (threshold 5 prior)"}
  ]
}
```

Scoring is asynchronous, so poll the assessment URL after a `POST` (it returns
`404` until the subscriber has produced the result — typically single-digit
milliseconds).

## The rules

Each rule is one class in `rules/`, is unit-tested in isolation, and returns a
contribution plus a reason. The score is the sum of the fired contributions,
clamped to `[0, 100]`; the decision is a threshold on the score (`REVIEW` at 40,
`DECLINE` at 70). All weights and thresholds are config (`fraudguard.*` in
`application.yml`).

| Rule (`id`) | What it catches | Weight |
| --- | --- | ---: |
| `VELOCITY_AMOUNT` | A charge far above the card's own recent average (≥ 5× the windowed mean, given ≥ 3 prior) — a tested card being cashed out. | 30 |
| `VELOCITY_FREQUENCY` | Too many charges on one card in a short window (≥ 5 prior in 10 min) — automated card testing. | 25 |
| `NEW_COUNTRY` | A country the card has never transacted in; a larger weight for a high-risk country (fires even first-seen). | 20 / 30 |
| `IMPOSSIBLE_TRAVEL` | Two distant countries closer in time than any travel allows (implied speed > 900 km/h) — the card in two places at once. | 40 |
| `ROUND_AMOUNT_HIGH_VALUE` | A suspiciously round, large amount (exact multiple of 100.00, ≥ 1000.00) — a manual cash-out figure. Weak alone; additive. | 15 |

Decision bands on the aggregated score: `0–39` → `APPROVE`, `40–69` → `REVIEW`,
`70–100` → `DECLINE`.

## Benchmark on real card-fraud data

The rules above are not only unit-tested in isolation — the **production scorer**
is run end to end over a **real, labelled card-transaction dataset** and measured
with proper imbalanced-classification metrics. The benchmark lives in
[`benchmark/`](src/main/java/com/angelo/fraudguard/benchmark) and is asserted by
`RealDataBenchmarkTest`.

**Dataset.** The [Sparkov *Credit Card Transactions Fraud Detection*](https://huggingface.co/datasets/pointe77/credit-card-transaction)
benchmark (Hugging Face mirror, Apache-2.0). It is a faithful fit because
FraudGuard scores transaction-level velocity and location, and this dataset
carries exactly those fields — card number, amount, event timestamp, merchant and
location — one row per transaction, labelled fraud/not. (The ULB `creditcard` set,
by contrast, exposes only PCA-anonymised components with no card id, time or
place, so a per-card velocity engine cannot run over it.) A 150,000-transaction
snapshot is vendored under
[`src/test/resources/data/`](src/test/resources/data/README.md) with its source,
licence and a one-command regeneration script. Fraud is **0.918 %** of rows —
heavily imbalanced, as real card fraud is.

**Method.** The dataset is split **by card** (never by transaction — a card's
whole history goes to one side, so velocity never leaks across the split) into a
development pool and a held-out set of unseen cards. We report a majority/random
baseline, 5-fold cross-validation on development, the held-out test, and a
**negative control** that shuffles the labels. Because the scorer is a fixed,
explainable expert system with no parameters fit to data, generalisation is
judged by **cross-validation ↔ held-out agreement**, not a train/test gap.

```
FraudGuard real-data benchmark
  dataset ...........: Sparkov Credit Card Transactions Fraud Detection (vendored snapshot)
  samples ...........: 150,000 transactions, 1,377 fraud (base rate 0.0092 = 0.918%)
  split .............: 111,414 dev (1,028 fraud) / 38,586 held-out (349 fraud), card-disjoint
  window / review / decline : PT720H / 40 / 70
  precision floor for recall@precision : 0.10

  baseline (majority / random):
    PR-AUC  = base rate   = 0.0092
    ROC-AUC = 0.5000

  5-fold cross-validation over development cards (mean +/- std):
    PR-AUC  (avg precision) = 0.0564 +/- 0.0039
    ROC-AUC                 = 0.7172 +/- 0.0191
    recall@precision        = 0.3660 +/- 0.1854

  development pool (all dev cards):
    PR-AUC = 0.0559   ROC-AUC = 0.7138   recall@precision = 0.4630

  HELD-OUT TEST (unseen cards):
    PR-AUC  (avg precision) = 0.0571   (6.3x the base rate of 0.0090)
    ROC-AUC                 = 0.7197
    recall@precision(0.10)  = 0.4756

  CV<->held-out agreement : |0.7172 - 0.7197| = 0.0025  (tolerance 0.0572)
  development - held-out ROC gap : -0.0058

  NEGATIVE CONTROL (labels shuffled on the held-out set):
    PR-AUC  = 0.0091  (-> base rate 0.0090)
    ROC-AUC = 0.5066  (-> 0.5)
    collapsed to chance : YES

  VERDICT : WELL_FIT
```

**Reading the result.**

- **It beats the baselines on unseen cards.** Held-out ROC-AUC **0.72** (vs 0.5
  for random) and PR-AUC **0.057** — **6.3×** the 0.9 % base rate a majority
  classifier scores. At a 10 % precision operating point it recovers **47.6 %** of
  held-out fraud, an order of magnitude above the base rate caught by random
  review.
- **It generalises.** Cross-validation and the held-out test agree to within
  **0.0025** ROC-AUC, far inside the fold noise — so this is not the luck of one
  split.
- **The signal is genuine.** With labels shuffled, PR-AUC falls to the base rate
  and ROC-AUC to 0.5: the control **collapses to chance**, which rules out leakage
  in the evaluation.
- **Verdict: well-fit** — clears the baseline, agrees across splits, and is not
  explained away by the control.

This is the discrimination a deterministic, fully explainable rule engine reaches
on real fraud with no training: a strong first-line filter whose every decision
carries a reason. The precision ceiling is the natural limit of a handful of
binary rules sharing one velocity window; the concrete ways to lift it —
per-rule windows (the amount-spike rule wants weeks, a burst rule wants minutes),
operator-calibrated weights, and a learned model contributing to the *same* 0–100
score behind the same audit trail — are exactly the extensions in
[the scope note](#a-note-on-scope).

**Run it:**

```bash
mvn -q test -Dtest=RealDataBenchmarkTest -Dsurefire.useFile=false   # prints the block above
```

## Design

### Why an event bus behind an interface

Scoring is decoupled from ingestion by the `EventBus` seam. The controller
depends only on `EventBus.publish(...)`; the scorer only on
`EventBus.subscribe(...)`. The shipped `InMemoryEventBus` is single-process and
fast, which keeps the demo and the tests free of any broker. The interface exists
so the transport can change without the business code changing: `KafkaEventBus`
(a documented stub) shows the drop-in — publish to a topic **keyed by `cardId`**
so a card's events share a partition and stay ordered, and run a consumer group
per subscriber for at-least-once delivery, durability and replay. See
[`docs/adr/0001`](docs/adr/0001-event-bus-abstraction.md).

### Why virtual threads

Delivery is one task per subscriber per event, and scoring is a short,
mostly-CPU step punctuated by cheap in-memory lookups. Java 21 virtual threads
fit this exactly: the event bus uses a per-task virtual-thread executor
(`config/VirtualThreadConfig`) and the web tier runs on virtual threads via
`spring.threads.virtual.enabled=true`. There is no pool size to tune and no queue
to overflow; a slow subscriber blocks only its own cheap thread. See
[`docs/adr/0002`](docs/adr/0002-virtual-threads.md).

### Ordering and consistency

Velocity features are order-sensitive: a rule must see the state that *preceded*
the event it scores. `VelocityStore.observe` is therefore atomic per card — it
snapshots and records under a per-card lock — so concurrent events for one card
cannot interleave their read-then-write, while different cards score
concurrently. The in-memory bus does not *guarantee* cross-event arrival order,
which is why the demo and tests await each assessment before sending the next,
and why the Kafka path keys by `cardId`: partition-per-card is how you get
durable per-card ordering in production. See
[`docs/adr/0004`](docs/adr/0004-in-memory-velocity-state.md).

### Why rules-first and explainable

A deterministic rule engine is a strong core for fraud: it is auditable, tunable
by operators, and correct on the clear cases without training data. Every outcome
carries a reason string built from the actual evidence (the ratio, the distance,
the count), so an analyst sees *why*. A model is a natural future signal, but it
belongs next to the rules feeding the same score — never replacing the
explanation. See [`docs/adr/0003`](docs/adr/0003-explainable-rule-engine.md).

## Testing

```bash
mvn test
```

The suite (JUnit 5 + Spring Boot Test, Awaitility for the async paths) covers:
each rule in isolation (fires when it should and not when it shouldn't), the
velocity store's sliding-window and pruning logic, the `RiskScorer` aggregation
and thresholds, the full event-driven flow through the real bus (a normal
transaction → `APPROVE`; a fraud sequence → `REVIEW`/`DECLINE` with the expected
rules), and the HTTP surface via MockMvc (202 ingest, assessment read-back,
validation `400`, unknown `404`). Async assessments are **awaited
deterministically** with Awaitility, never slept on.

It also covers the real-data benchmark: the metric implementations against
hand-computed values (perfect separation → AUC 1.0, inverted → 0.0), the loader
reading the vendored snapshot with the expected shape and label distribution, the
harness reproducing `ScoringService`'s scores transaction-by-transaction, the
scorer beating the baseline on held-out cards, the negative control collapsing to
chance, and the fit verdict refusing to call a shuffled run well-fit.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — components, data flow, rationale.
- [`docs/adr/`](docs/adr/) — the key design decisions and their trade-offs.
- [`docs/estudio/`](docs/estudio/) — a study guide in Spanish, to learn the project in depth.

## A note on scope

FraudGuard is a reference implementation, not a production fraud platform. It
shows a clean, explainable, event-driven pipeline that runs and is fully tested
in one process. A real deployment would:

- **Replace the in-memory bus with Kafka** (topics keyed by `cardId` for per-card
  ordering), making delivery durable, replayable and horizontally scalable.
- **Move velocity state into a feature store** (e.g. Redis or a streaming
  aggregator) so it is shared across instances, bounded, and survives restarts —
  the in-memory store is process-local and grows without eviction.
- **Add a learned model alongside the rules** — a gradient-boosted or neural
  scorer contributing to the same 0–100 score, with the rules still providing the
  floor of explainable, regulator-defensible decisions and the model's features
  logged for the same audit trail.

The heuristics here (a country-centroid distance table, an illustrative high-risk
list, fixed weights) are deliberate stand-ins for those production components, not
claims about real-world fraud rates.

## License

[MIT](LICENSE) © Angelo David Padilla Romero
