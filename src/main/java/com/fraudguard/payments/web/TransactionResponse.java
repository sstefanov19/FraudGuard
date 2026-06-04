package com.fraudguard.payments.web;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.persistence.TransactionEntity;

import java.time.Instant;
import java.util.List;

public record TransactionResponse(
        String transactionId,
        Decision decision,
        Double score,
        boolean degraded,
        Instant decidedAt,
        List<RiskFactorResponse> factors) {

    public static TransactionResponse from(String transactionId, FraudDecision decision) {
        Double score = Double.isNaN(decision.score()) ? null : decision.score();
        return new TransactionResponse(
                transactionId,
                decision.decision(),
                score,
                decision.degraded(),
                decision.decidedAt(),
                decision.factors().stream().map(RiskFactorResponse::from).toList());
    }

    public static TransactionResponse fromStored(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId(),
                entity.getDecision(),
                entity.getScore(),
                entity.isDegraded(),
                entity.getScoredAt(),
                List.of());
    }
}
