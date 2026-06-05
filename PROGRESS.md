# FraudGuard — Build Progress

Real-time, explainable fraud detection for a payments platform.
Last updated: 2026-06-04

## Where we are

**Steps 1–2 of 5 are complete and tests pass (40/40 green).** The fraud-decision engine
works, and it is now wired into a Postgres-backed `POST /transactions` API with bounded
feature queries and idempotency.

```
MVP progress  ▓▓▓▓▓▓▓▓░░░░░░░░░░░░  ~40%   (engine + Postgres feature/API slice done)
```

The riskiest logic is locked down early, and the first infrastructure slice is now
exercised end-to-end over a real Postgres database in tests.

## What "MVP" means here

Build-order steps 1–5 (ML and the Fraud Lab tuning console are explicitly deferred):

| # | Milestone | Status |
|---|-----------|--------|
| 1 | Domain model + rule-based scorer, synchronous, unit-tested | ✅ **DONE** (verified) |
| 2 | Feature layer (bounded Postgres queries for velocity / trailing avg) | ✅ **DONE** (verified) |
| 3 | Kafka-via-Redpanda streaming (direct async publish) + persistence | ⬜ not started |
| 4 | WebSocket live feed + dashboard backend | ⬜ not started |
| 5 | Transaction simulator (legit + fraud campaigns) | ⬜ not started |
| — | Next.js frontend (live feed + breakdown screens) | ⬜ not started |

## Step 2 — DONE (verified `mvn test`, 40 tests, 0 failures)

The feature layer is now a full vertical slice over Postgres:

- **Persistence** — Flyway-managed `transactions` table, JPA entity/repository, unique
  `idempotency_key`, status, and decision summary fields.
- **Feature provider** — bounded Postgres queries compute transaction-derived velocity,
  trailing average, prior count, and new-device signals.
- **REST API** — `POST /transactions` validates input, persists `RECEIVED`, flushes,
  computes features, scores, stores the final decision/status, and returns the explanation.
- **Idempotency** — repeated `Idempotency-Key` calls return the stored decision without a
  duplicate transaction row.
- **Degradation** — feature-provider failure is now covered by fail-to-REVIEW, not only
  scorer failure.

Intentional limitation for this step: the live API sets `accountHomeCountry = null` and
blocklist flags to `false`, so `GeoMismatchRule` and `BlocklistRule` are dormant in the
wired API until accounts/blocklist tables land later. Their unit tests still exercise those
rules directly.

Known limitation: risk factors are not persisted, so an idempotent replay (`POST /transactions`
with a previously-seen `Idempotency-Key`) returns the stored decision/score/degraded flag but an
empty `factors` list. The first response carries the full explanation; retries do not. Persisting
factors for full replay explainability is deferred to a later step.

Known limitation: velocity counting is correct only for serialized same-account requests. Scoring
runs at the default READ COMMITTED isolation, so a burst fired in parallel can have each request
count only itself and slip under the velocity threshold. The card-testing test proves the guarantee
sequentially; concurrency-hardening (per-account advisory lock or SERIALIZABLE + retry) is tracked
in TODOS.md.

## Step 1 — DONE (verified `mvn test`, 33 tests, 0 failures)

The decision engine, built as pure logic with no database so it could be fully unit-tested:

- **Money** — `BigDecimal` + currency value object; exact decimals, refuses cross-currency comparison.
- **Domain** — `Account`, `Card`, `Transaction` (with fraud dimensions: device, merchant+category,
  BIN/country, billing country), `TransactionStatus` state machine (RECEIVED → SCORED → APPROVED/BLOCKED/IN_REVIEW).
- **Rule engine** — `Rule` interface, one class per rule, each emitting an explainable `RiskFactor`
  (reason code + normalized severity × weight = contribution):
  - `VelocityRule` (card-testing), `AmountAnomalyRule`, `GeoMismatchRule`,
    `NewDeviceHighValueRule`, `BlocklistRule`.
- **Scorer** — `FraudScorer` interface + `RuleBasedScorer` (sums contributions → score → decision via
  tunable `DecisionThresholds`).
- **ScoringService** — the degradation guarantee: if scoring throws, **fail to REVIEW**, never silent approve.

Headline tests proven green:
- ✅ `card_testing_burst_blocks` — 4th $1 charge in 60s → BLOCK
- ✅ `when_scoring_throws_it_fails_to_review_not_approve` — outage → REVIEW, not approve
- ✅ `a_mundane_purchase_is_approved_with_no_risk_factors` — no false positives
- ✅ `foreign_high_value_charge_on_a_new_device_is_reviewed_not_blocked` — stacked soft signals → REVIEW

## Eng-review task tracker (T1–T11)

| Task | What | Status |
|------|------|--------|
| T1 | Domain model (money, idempotency, state machine, dimensions) | ✅ done, including JPA persistence |
| T2 | Rule scorer + normalized scores/reason codes | ✅ done |
| T3 | Inline scoring in `POST /transactions` (web layer) | ✅ done |
| T4 | Fail-to-REVIEW on scorer/feature failure (CRITICAL) | ✅ done, scorer + feature provider |
| T5 | Idempotency-Key handling | ✅ done |
| T6 | Postgres bounded velocity / trailing-avg queries | ✅ done |
| T7 | Versioned event contracts + async Redpanda publish | ⬜ not started |
| T8 | WebSocket push + feed throttle | ⬜ not started |
| T9 | Next.js live feed + breakdown screen | ⬜ not started |
| T10 | Simulator + fraud campaigns | ⬜ not started |
| T11 | docker-compose + GitHub Actions CI | ⬜ not started |

## Design task tracker (D1–D7)

All ⬜ not started. Specs locked in the design doc; wireframe approved.
D1 pill component, D2 feed (mobile-first→desktop), D3 connection state, D4 breakdown drawer,
D5 interaction states, D6 run /design-consultation (needs OpenAI key), D7 a11y baseline.

## Next up

1. **Streaming (T7)** — versioned decision events and direct async publish to Redpanda.
2. **Live feed backend (T8)** — WebSocket push and throttled feed projection.
3. **Dashboard/frontend (T9)** — live feed and breakdown views.

## Known deviations from the plan (intentional)
- **Single Maven module** (not multi-module Gradle yet) — no decoupling to do until the
  streaming/feature modules exist; package boundaries (`payments`, `fraud`) keep the split cheap.
- **Maven, not Gradle** — your call.
- Rules are **pure functions of `(Transaction, FeatureSnapshot)`** so step 1 needs no database.

## How to run the tests
```bash
export JAVA_HOME=/Users/stefanstefanov/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home
mvn test
```
