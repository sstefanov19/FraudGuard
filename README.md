# FraudGuard

**Real-time, explainable fraud detection for a payments platform.**

FraudGuard scores a payment authorization the moment it arrives and returns a decision —
`APPROVE`, `REVIEW`, or `BLOCK` — together with the exact reasons behind it. Every decision
is persisted, idempotent, and (as the streaming layer lands) published to Kafka for
downstream consumers like a live ops dashboard.

---

## The problem

A payments platform has to answer one question on every authorization, in the few hundred
milliseconds before it approves a charge:

> Is this transaction fraudulent, and if so, how sure are we?

Getting that wrong is expensive in both directions:

- **Approve fraud** and the business eats chargebacks, fines, and lost goods.
- **Block legitimate spend** and you lose the sale and annoy a real customer.

Two things make this hard in production, and they are the things FraudGuard is built around:

1. **Decisions must be explainable.** "Risk score 0.81, blocked" is useless to a fraud
   analyst, a chargeback dispute, or a regulator. Every decision needs to name *which*
   signals fired and by how much. FraudGuard treats the reason breakdown as a first-class
   part of the API contract, not a log line.

2. **The engine must never silently approve when it's broken.** If the feature store is
   unreachable or scoring throws, the safe failure is to hold the transaction for `REVIEW`,
   loudly — never to wave it through. FraudGuard makes "fail to REVIEW" a guarantee enforced
   in one place that is hard to get wrong.

---

## What it does today

Send a payment authorization to `POST /transactions`. FraudGuard:

1. Validates the request and rejects anything it can't honor with a `400` (not a `500` or a
   fake "scoring outage").
2. Persists the transaction, then computes **features** from history (velocity, trailing
   spend average, new-device signals) using bounded Postgres queries.
3. Runs every fraud **rule** against the transaction + features, summing each rule's
   contribution into a single risk score in `[0, 1]`.
4. Maps the score to a decision via tunable thresholds, stores the decision, and returns the
   full explanation.

### The decision model

The score is a weighted sum of the risk factors that fired:

```
score    = clamp01( Σ factor.contribution )      // contribution = severity × weight
decision = APPROVE   if score < 0.40
           REVIEW    if 0.40 ≤ score < 0.80
           BLOCK     if score ≥ 0.80
```

Thresholds and rule weights are **data, not magic numbers** (`DecisionThresholds`,
`FraudConfiguration`) so they can be tuned without touching rule logic — the seam for a
future Fraud Lab tuning console.

### The rules

| Rule | Reason code | Signal | Behavior |
|------|-------------|--------|----------|
| **Velocity (1 min)** | `VELOCITY_1M` | Card-testing / automated abuse | Tuned so the 4th charge in 60s reaches `BLOCK` on its own |
| **Velocity (1 hour)** | `VELOCITY_1H` | Sustained abuse | Severity ramps with overage; supporting weight |
| **Amount anomaly** | `AMOUNT_ANOMALY` | Stolen card / bust-out | Fires when amount ≫ the account's trailing average (needs history) |
| **Geo mismatch** | `GEO_COUNTRY_MISMATCH` | Account takeover / stolen card | Weighted into the `REVIEW` band — a foreign charge is often just travel |
| **New device + high value** | `NEW_DEVICE_HIGH_VALUE` | Account takeover | Weak alone, meaningful together; small supporting weight |
| **Blocklist** | `BLOCKLIST_HIT` | Known-bad card / IP / device | Hard signal: full severity and weight → drives `BLOCK` alone |

Each rule emits a `RiskFactor` with a stable machine-readable `ReasonCode`, a human-readable
description carrying the concrete numbers, and its `severity × weight` contribution.

### Example response

```json
POST /transactions
Idempotency-Key: auth_20260605_000001

{
  "transactionId": "tx_5a83c820-ec64-4418-87a8-9b145dcbca44",
  "decision": "BLOCK",
  "score": 0.72,
  "degraded": false,
  "decidedAt": "2026-06-05T10:15:30Z",
  "factors": [
    { "reasonCode": "VELOCITY_1M", "description": "4 transactions in the last minute (threshold 3)" }
  ]
}
```

**Idempotency:** the client-supplied `Idempotency-Key` header dedupes payment retries — a
repeated key returns the stored decision instead of creating a second transaction row.

---

## Architecture

```
                POST /transactions
                       │
              TransactionController        REST + Bean Validation (400s before any DB write)
                       │
               TransactionService          persistence, idempotency, txn boundary, degradation
                  │          │
        FeatureProvider   ScoringService    "fail to REVIEW, never silent-approve"
         (Postgres)            │
                          RuleBasedScorer   Σ contributions → score → DecisionThresholds
                               │
                          [ Rules ]         one class per signal, each → RiskFactor
                               │
                     FraudDecisionRecorded  app event, published AFTER_COMMIT
                               │
                     Kafka (fraud.decisions.v1)   language-neutral JSON contract
```

- **`com.fraudguard.fraud`** — the decision engine: rules, scorer, thresholds, scoring
  service. Pure logic; all I/O arrives as a `FeatureSnapshot`, which makes it fully
  unit-testable.
- **`com.fraudguard.payments`** — domain model (`Money`, `Transaction`, `Account`, `Card`),
  Postgres persistence, feature queries, and the REST layer.
- **`com.fraudguard.kafka`** — the `FraudDecisionEvent` wire contract and publisher,
  deliberately decoupled from the web/domain DTOs so a REST tweak can't silently break a
  downstream consumer. Events publish only after the decision commits, keyed by `accountId`
  for per-account ordering.

---

## Tech stack

- **Java 21**, **Spring Boot 3.4** (Web, Data JPA, Validation)
- **PostgreSQL** with **Flyway**-managed migrations (`ddl-auto: validate` — schema is owned
  by migrations, not Hibernate)
- **Spring Kafka** → **Redpanda** for decision streaming
- **springdoc-openapi** — Swagger UI at `/swagger-ui.html`
- **JUnit 5, Mockito, AssertJ, Testcontainers** (real Postgres + Redpanda in integration tests)

---

## Running locally

**Prerequisites:** JDK 21, Maven, Docker.

1. Start Postgres (database `fraudguard`, user/pass `postgres`/`postgres` per
   `application.properties`) and the dev Redpanda broker:

   ```bash
   docker compose up -d        # Redpanda on :9092, console on http://localhost:8085
   ```

2. Run the app:

   ```bash
   mvn spring-boot:run
   ```

   - API + Swagger UI: http://localhost:8080/swagger-ui.html
   - Kafka decisions stream to topic `fraud.decisions.v1` (watch them in the Redpanda console)

3. Run the tests (spins up Postgres and Redpanda via Testcontainers — no manual setup):

   ```bash
   mvn test
   ```

---

## Project status

This is built in vertical slices, riskiest logic first. ML scoring and the Fraud Lab tuning
console are explicitly deferred.

| # | Milestone | Status |
|---|-----------|--------|
| 1 | Domain model + rule-based scorer, synchronous, unit-tested | ✅ Done |
| 2 | Feature layer (bounded Postgres queries) + `POST /transactions` + idempotency | ✅ Done |
| 3 | Kafka-via-Redpanda decision streaming | 🚧 In progress |
| 4 | WebSocket live feed + dashboard backend | ⬜ Not started |
| 5 | Transaction simulator (legit + fraud campaigns) | ⬜ Not started |
| — | Next.js frontend (live feed + breakdown screens) | ⬜ Not started |

### Known limitations

- **Geo / blocklist rules are dormant in the wired API.** The live feature provider currently
  sets `accountHomeCountry = null` and blocklist flags to `false` (accounts/blocklist tables
  land in a later step), so `GeoMismatchRule` and `BlocklistRule` don't fire end-to-end yet.
  Their unit tests exercise them directly.
- **Idempotent replays lose the factor breakdown.** Risk factors aren't persisted, so a
  repeated `Idempotency-Key` returns the stored decision/score with an empty `factors` list.
  The first response carries the full explanation.
- **Velocity counting assumes serialized same-account requests.** Scoring runs at READ
  COMMITTED, so a parallel burst can let each request count only itself. Concurrency hardening
  (per-account lock or `SERIALIZABLE` + retry) is tracked in `plans/TODOS.md`.
