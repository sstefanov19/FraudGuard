package com.fraudguard.payments.web;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.persistence.TransactionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Fraud decision returned after a payment authorization is scored.")
public record TransactionResponse(
        @Schema(description = "Generated transaction identifier.", example = "tx_5a83c820-ec64-4418-87a8-9b145dcbca44")
        String transactionId,

        @Schema(description = "Final fraud decision.")
        Decision decision,

        @Schema(description = "Normalized fraud score from 0.0 to 1.0. Null only when no score could be produced.", example = "0.72")
        Double score,

        @Schema(description = "Whether FraudGuard fell back to REVIEW because scoring or feature loading failed.", example = "false")
        boolean degraded,

        @Schema(description = "Timestamp when the decision was produced.", example = "2026-06-05T10:15:30Z")
        Instant decidedAt,

        @Schema(description = "Explainable risk factors that contributed to the score.")
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
