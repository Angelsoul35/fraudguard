#!/usr/bin/env python3
"""Regenerate the vendored FraudGuard benchmark snapshot.

It pulls the first ROWS transactions of the *train* split of the Sparkov
"Credit Card Transactions Fraud Detection" dataset from the Hugging Face
datasets-server rows API (kaggle.com is not required), keeps only the columns
FraudGuard's scorer needs, and writes a gzip-compressed CSV.

The dataset is served in ascending transaction-time order, so taking the first
ROWS rows yields a contiguous ~2.5-month slice in which every card has a
multi-week history — exactly what the per-card velocity rules need. The result
is deterministic: the same ROWS always produce the same bytes.

Usage:
    python3 fetch_snapshot.py                 # writes creditcard_fraud_sample.csv.gz
    python3 fetch_snapshot.py 150000 out.csv.gz

Dataset : https://huggingface.co/datasets/pointe77/credit-card-transaction
Columns kept: unix_time, cc_num, amt, category, state, is_fraud
"""
import csv
import gzip
import io
import json
import sys
import time
import urllib.parse
import urllib.request

DATASET = "pointe77/credit-card-transaction"
BASE = "https://datasets-server.huggingface.co/rows"
COLUMNS = ["unix_time", "cc_num", "amt", "category", "state", "is_fraud"]
PAGE = 100  # the rows API caps length at 100


def fetch(offset, length=PAGE):
    query = urllib.parse.urlencode({
        "dataset": DATASET, "config": "default", "split": "train",
        "offset": offset, "length": length,
    })
    url = BASE + "?" + query
    last = ""
    for attempt in range(12):
        try:
            with urllib.request.urlopen(url, timeout=90) as resp:
                payload = resp.read().decode("utf-8")
            if '"rows"' in payload:
                return [item["row"] for item in json.loads(payload)["rows"]]
            last = payload[:120]
        except Exception as exc:  # transient 5xx / network: back off and retry
            last = f"{type(exc).__name__}: {exc}"
        time.sleep(min(20, 2 + 2 * attempt))
    raise RuntimeError(f"failed to fetch offset {offset}: {last}")


def main():
    rows_wanted = int(sys.argv[1]) if len(sys.argv) > 1 else 150_000
    out_path = sys.argv[2] if len(sys.argv) > 2 else "creditcard_fraud_sample.csv.gz"

    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(COLUMNS)
    written = 0
    for offset in range(0, rows_wanted, PAGE):
        for row in fetch(offset, min(PAGE, rows_wanted - offset)):
            writer.writerow([row[c] for c in COLUMNS])
            written += 1
        if offset % 10_000 == 0:
            print(f"  ...{offset}/{rows_wanted}", file=sys.stderr)

    data = buffer.getvalue().encode("utf-8")
    with gzip.open(out_path, "wb") as gz:
        gz.write(data)
    print(f"wrote {written} rows to {out_path}")


if __name__ == "__main__":
    main()
