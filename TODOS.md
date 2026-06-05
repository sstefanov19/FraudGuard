# TODOS — payment-platform-fraud-detection

Deferred items from the eng review (2026-06-04). Each was a deliberate decision to
sequence as a later upgrade, not an oversight. Order roughly reflects when to pick them up.

## Redis low-latency feature store (upgrade)
- **What:** Move velocity / trailing-average features from Postgres queries to Redis
  (bucketed per-minute counters, or sorted-set sliding windows).
- **Why:** Demonstrates the latency tradeoff and the feature-store pattern; a strong
  before/after story for the README.
- **Depends on:** MVP feature layer working on Postgres first (T6).
- **Where to start:** Wrap feature reads behind a `FeatureSource` interface in T6 so the
  Redis impl is a drop-in swap; write the before/after latency numbers.

## Transactional outbox (reliability upgrade)
- **What:** Write events to an outbox table in the same DB txn; a relay (scheduled poller,
  or Debezium CDC) publishes to Redpanda. Guarantees no lost decision/audit events.
- **Why:** Closes the one accepted risk in the MVP (async publish can drop an audit event
  on broker failure). Interview-worthy distributed-systems pattern.
- **Depends on:** Direct async publish working (T7).
- **Where to start:** Add `outbox` table + a `@Scheduled` relay; document the failure
  scenario it fixes.

## ML scorer (build-order step 6)
- **What:** Train a model (logistic regression / gradient boosting) on simulator-labeled
  data; add `MlScorer` + `CompositeScorer` behind the existing `FraudScorer` interface;
  compare rules-only vs rules+ML precision/recall.
- **Why:** The data-science half of fraud; rounds out the portfolio story.
- **Depends on:** Simulator emitting labeled traffic (T10); the `FraudScorer` seam (T2).
- **Caveat:** Simulator-labeled data only teaches patterns you coded in — note this honestly.

## Fraud Lab tuning console (build-order step 7)
- **What:** Threshold-tuning UI with live precision/recall + false-positive-rate as you
  slide rule weights; scenario library of fraud campaigns.
- **Why:** The signature portfolio differentiator — "a fraud lab you can play with."
- **Depends on:** MVP + normalized rule scores/weights (T2) so thresholds are tunable.

## Concurrent velocity correctness (hardening)
- **What:** Make the velocity / card-testing counts correct under concurrent same-account
  requests. Today scoring runs at the default READ COMMITTED isolation, so parallel
  authorizations for one account don't see each other's uncommitted rows and can each
  count only themselves — a burst fired in parallel can slip under the velocity threshold
  and all approve.
- **Why:** Catching card-testing bursts is the headline fraud guarantee; the current
  integration test only proves it for *sequential* requests, and real attackers parallelize.
- **Depends on:** Postgres feature layer (T6, done).
- **Where to start:** Take a per-account Postgres advisory lock at the start of the scoring
  transaction (`SELECT pg_advisory_xact_lock(hashtext(:accountId))`), or run scoring at
  SERIALIZABLE isolation with retry-on-serialization-failure. Add a concurrency test that
  fires N parallel same-account charges and asserts the burst still blocks.

## Currency-aware rule thresholds (multi-currency support)
- **What:** Make fixed-threshold rules hold a per-currency threshold instead of one hardcoded USD
  value. Today `NewDeviceHighValueRule` is wired with `Money.of("1000.00", "USD")` and compares it
  against every transaction amount, so any non-USD amount throws on cross-currency comparison and the
  engine degrades to REVIEW. The API now rejects non-USD up front (allowlist `fraud.supported-currencies`,
  default `USD`); this TODO is the real fix that lets the engine score non-USD natively.
- **Why:** Unblocks genuine multi-currency traffic; removes the "we advertise ISO currencies but only
  USD works" gap.
- **Depends on:** A per-currency threshold config (and likely FX or per-currency tuning).
- **Where to start:** Give `NewDeviceHighValueRule` a `Map<Currency, Money>` of thresholds (or resolve
  the threshold by the transaction's currency); then widen the allowlist as each currency is tuned.

## REVIEW case-management queue UI
- **What:** Analyst screen listing IN_REVIEW transactions with approve/block actions.
- **Why:** Completes the fail-to-REVIEW story; realistic fraud-ops feature.
- **Depends on:** REVIEW case stub from T1/T3.
