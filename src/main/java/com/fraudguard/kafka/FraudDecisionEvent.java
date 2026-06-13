package com.fraudguard.kafka;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.Factor;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.domain.Transaction;

import java.time.Instant;
import java.util.List;

/**
 * The wire contract published to Kafka after a fraud decision commits — a promise to downstream
 * consumers (first: the M4 live dashboard), deliberately decoupled from the web/domain DTOs so a
 * REST tweak cannot silently break a consumer.
 */
public record FraudDecisionEvent(
        String eventId,       // UUID - consumer-side idempotency
        String transactionId, // correlate back to the txn
        String accountId,     // also the Kafka KEY -> per account ordering
        Decision decision,    // APPROVE | REVIEW | BLOCK (serialized as string)
        Double score,         // NULLABLE - null when degraded
        boolean degraded,
        Instant decidedAt,    // ISO-8601 string on the wire
        List<Factor> factors) {

    public FraudDecisionEvent {
        factors = List.copyOf(factors); // defensive immutable copy
    }

    /**
     * Maps a committed decision to its wire event. {@code eventId} is supplied by the caller (the
     * publisher) so this stays a pure, deterministic mapping the tests can pin. {@code score} is null
     * when degraded — {@link FraudDecision} carries {@code Double.NaN} there and JSON has no NaN.
     */
    public static FraudDecisionEvent of(Transaction txn, FraudDecision decision, String eventId) {
        Double score = Double.isNaN(decision.score()) ? null : decision.score();
        return new FraudDecisionEvent(
                eventId,
                txn.id(),
                txn.accountId(),
                decision.decision(),
                score,
                decision.degraded(),
                decision.decidedAt(),
                decision.factors().stream().map(Factor::from).toList());
    }
}
