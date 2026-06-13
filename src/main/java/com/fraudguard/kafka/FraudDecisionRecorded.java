package com.fraudguard.kafka;

import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.domain.Transaction;

/**
 * In-process Spring application event published INSIDE the scoring DB transaction so an
 * {@code AFTER_COMMIT} listener can turn it into a Kafka event only once the decision is durably
 * committed. Keeps the wire-format mapping out of {@code TransactionService}.
 */
public record FraudDecisionRecorded(Transaction transaction, FraudDecision decision) {
}
