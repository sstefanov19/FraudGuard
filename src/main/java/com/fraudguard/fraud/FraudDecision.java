package com.fraudguard.fraud;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of scoring a transaction: the verdict, the numeric score, and the full list of
 * reasons behind it. This is what gets persisted and what the breakdown screen renders.
 *
 * <p>{@code degraded} is true only when scoring could not run (e.g. the feature store was down)
 * and the engine fell back to a safe {@link Decision#REVIEW}. A degraded decision carries a single
 * {@link ReasonCode#SCORING_UNAVAILABLE} factor so the reason is visible, never silent.
 */
public record FraudDecision(
        Decision decision,
        double score,
        List<RiskFactor> factors,
        Instant decidedAt,
        boolean degraded) {

    public FraudDecision {
        factors = List.copyOf(factors); // defensive immutable copy
    }

    /** Safe fallback when scoring itself failed — hold the transaction for review, loudly. */
    public static FraudDecision degradedReview(Instant decidedAt) {
        RiskFactor unavailable = new RiskFactor(
                ReasonCode.SCORING_UNAVAILABLE,
                "Scoring engine unavailable; held for manual review",
                1.0, 1.0);
        return new FraudDecision(Decision.REVIEW, Double.NaN, List.of(unavailable), decidedAt, true);
    }
}
