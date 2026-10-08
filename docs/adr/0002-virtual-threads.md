# ADR 0002 — Java 21 virtual threads for event delivery and the web tier

## Status
Accepted.

## Context
The event bus delivers one task per subscriber per event, and scoring is a short
step: a couple of in-memory lookups and some arithmetic. Under load there can be
many such tasks in flight at once. A classic fixed thread pool forces a choice
between a small pool (which serializes bursts and couples unrelated work) and a
large pool (which wastes memory on mostly-idle platform threads), plus a bounded
queue that can overflow — all knobs to mis-tune.

## Decision
Use Java 21 virtual threads throughout:

- The event bus runs on a dedicated per-task virtual-thread executor
  (`Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)`, see
  `config/VirtualThreadConfig`). Every event delivery gets a fresh virtual thread.
- The web tier runs on virtual threads via `spring.threads.virtual.enabled=true`.

## Consequences
- "One thread per task" becomes the simplest correct model: no pool size to tune,
  no queue to overflow, and a blocked task costs a few hundred bytes rather than a
  platform thread.
- A slow or blocking subscriber parks only its own virtual thread; it does not
  starve a shared pool or delay the publisher, which returns as soon as the tasks
  are submitted.
- The executor is closed with the application context, so delivery threads shut
  down cleanly.
- Virtual threads suit this I/O-light, high-fan-out workload well. They are **not**
  a license to share mutable state carelessly: the per-card serialization in the
  velocity store (ADR 0004) is what actually makes concurrent scoring correct —
  virtual threads only make running many of them cheap.
