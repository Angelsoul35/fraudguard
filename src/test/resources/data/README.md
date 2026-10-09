# Benchmark dataset — real card-transaction fraud

This directory vendors the labelled dataset the FraudGuard benchmark runs on. It
is read by `FraudDatasetLoader` and exercised end to end by the tests in
`com.angelo.fraudguard.benchmark`.

## Source

- **Dataset:** *Credit Card Transactions Fraud Detection* (the "Sparkov" set).
- **Mirror used:** Hugging Face — [`pointe77/credit-card-transaction`](https://huggingface.co/datasets/pointe77/credit-card-transaction),
  `default` config, `train` split (1,296,675 rows).
- **License:** Apache-2.0 (as declared by the Hugging Face mirror). The data was
  produced by the open-source Sparkov transaction generator (Brandon Harris),
  and is one of the standard public benchmarks for card-fraud detection.

## Why this dataset is a faithful fit for FraudGuard

FraudGuard scores **transaction-level** signals: a card's spending velocity, a
charge far above the card's own recent average, repeat activity, and location.
The scorer therefore needs real per-transaction fields — a card id, an amount, an
event timestamp, and a location — in event-time order.

This dataset carries exactly those fields: `cc_num` (card), `amt` (amount),
`unix_time` (event time), `category`/`merchant`, and US `state`/`lat`/`long`,
each row labelled `is_fraud`. That is what lets the benchmark run FraudGuard's
**production** scorer unchanged over it (see `ScoringHarness`, pinned to
`ScoringService` by `ScoringHarnessEquivalenceTest`).

By contrast, the well-known ULB `creditcard` set exposes only PCA-anonymised
components `V1..V28` plus `Amount` — it has no card id, timestamp, or location,
so a per-card velocity engine cannot be run over it without inventing those
fields. This dataset keeps the evaluation aligned with what the service actually
computes.

Two properties of the data drive the result:

- Fraud amounts skew far larger than legitimate ones (mean ≈ \$511 vs ≈ \$68 in
  the snapshot), which is precisely the amount-velocity signal the scorer
  encodes.
- Transactions are US-domestic, so the impossible-travel and new-country rules do
  not fire here; they remain active and are covered by their own unit tests, and
  would carry signal on a multi-country stream. The amount-velocity rule carries
  the signal on this dataset.

## The vendored snapshot

`creditcard_fraud_sample.csv.gz` — the first **150,000** transactions of the
train split (a contiguous ~2.5-month window), trimmed to the columns the scorer
uses:

```
unix_time,cc_num,amt,category,state,is_fraud
```

- 150,000 transactions, **1,377 fraud** → base rate **0.918 %** (heavily
  imbalanced, as real card fraud is).
- 928 distinct cards; each has a multi-week history in the window, so per-card
  velocity features are meaningful.
- Taking a contiguous time-ordered prefix (rather than a random sample) keeps
  each card's transactions in order, which the velocity store relies on.

The file is gzip-compressed (~2 MB); `FraudDatasetLoader` decompresses it
transparently.

## Regenerating the snapshot

`fetch_snapshot.py` reproduces the exact bytes from the Hugging Face rows API
(deterministic — the split is served in transaction-time order):

```bash
cd src/test/resources/data
python3 fetch_snapshot.py 150000 creditcard_fraud_sample.csv.gz
```

The rows API endpoint it calls, for reference:

```
https://datasets-server.huggingface.co/rows?dataset=pointe77%2Fcredit-card-transaction&config=default&split=train&offset=<N>&length=100
```
