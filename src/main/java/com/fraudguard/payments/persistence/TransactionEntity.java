package com.fraudguard.payments.persistence;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.domain.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Embedded
    private TransactionDetails details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Embedded
    private DecisionSummary decisionSummary;

    protected TransactionEntity() {
    }

    public static TransactionEntity fromDomain(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.id = transaction.id();
        entity.idempotencyKey = transaction.idempotencyKey();
        entity.details = TransactionDetails.from(transaction);
        entity.status = TransactionStatus.RECEIVED;
        entity.decisionSummary = DecisionSummary.pending();
        return entity;
    }

    public Transaction toDomain() {
        return details.toDomain(id, idempotencyKey);
    }

    public void transitionTo(TransactionStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Cannot transition transaction %s from %s to %s".formatted(id, status, next));
        }
        status = next;
    }

    public void applyDecision(FraudDecision fraudDecision) {
        transitionTo(TransactionStatus.SCORED);
        decisionSummary = DecisionSummary.from(fraudDecision);
        transitionTo(statusFor(fraudDecision.decision()));
    }

    private static TransactionStatus statusFor(Decision decision) {
        return switch (decision) {
            case APPROVE -> TransactionStatus.APPROVED;
            case BLOCK -> TransactionStatus.BLOCKED;
            case REVIEW -> TransactionStatus.IN_REVIEW;
        };
    }

    public String getId() {
        return id;
    }

    public Decision getDecision() {
        return decisionSummary.decision();
    }

    public Double getScore() {
        return decisionSummary.score();
    }

    public Instant getScoredAt() {
        return decisionSummary.scoredAt();
    }

    public boolean isDegraded() {
        return decisionSummary.degraded();
    }
}
