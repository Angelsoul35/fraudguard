# ADR 0003 — An explainable rule engine, not a black-box model

## Status
Accepted.

## Context
A card issuer must be able to justify a declined or held transaction — to a
regulator, to a fraud analyst, and to the cardholder on the phone. "The model
said so" is not an acceptable answer. The service also has to make sound
decisions on day one, before any labelled fraud data exists, and let operators
tune behaviour as attack patterns shift.

## Decision
Make the scoring core a **rule engine of explainable rules**:

- A `Rule` is a pure function of a `RuleContext` (the transaction + the pre-event
  velocity snapshot). It returns a `RuleOutcome` that either did not fire (zero
  contribution) or fired with a positive contribution **and a human-readable
  reason built from the actual evidence** — the ratio, the distance, the count.
- The five rules (amount spike, frequency, new/high-risk country, impossible
  travel, round high-value) are one class each, independent, and unit-tested in
  isolation.
- `RiskScorer` aggregates by **summation**, clamps to `[0, 100]`, and derives the
  decision by thresholds. Summation is deliberate: the assessment can state
  exactly which rules produced the number and by how much.
- Every threshold and weight is configuration (`fraudguard.*`), so rules are
  operator dials, not code changes.

## Consequences
- Every decision is auditable: the assessment lists the fired rules with reasons,
  and the score is their transparent sum.
- Adding a rule is adding a `@Component implements Rule`; the engine injects the
  full list, so there is no registry to update.
- Summed weights can double-count correlated signals (a fraud burst often trips
  frequency *and* impossible travel *and* new country at once). We accept this —
  it pushes clearly-bad transactions decisively over the `DECLINE` line — and bound
  it by clamping to 100.
- A learned model is a natural future addition, but as **one more contributor to
  the same score** with its features logged for the audit trail — never as an
  unexplained override of the rule-based floor. See the README scope note.
