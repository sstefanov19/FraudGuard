package com.fraudguard.payments.persistence;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
class DecisionSummary {

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Decision decision;

    @Column
    private Double score;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Column(nullable = false)
    private boolean degraded;

    protected DecisionSummary() {
    }

    private DecisionSummary(Decision decision, Double score, Instant scoredAt, boolean degraded) {
        this.decision = decision;
        this.score = score;
        this.scoredAt = scoredAt;
        this.degraded = degraded;
    }

    static DecisionSummary pending() {
        return new DecisionSummary(null, null, null, false);
    }

    static DecisionSummary from(FraudDecision fraudDecision) {
        Double score = Double.isNaN(fraudDecision.score()) ? null : fraudDecision.score();
        return new DecisionSummary(
                fraudDecision.decision(),
                score,
                fraudDecision.decidedAt(),
                fraudDecision.degraded());
    }

    Decision decision() {
        return decision;
    }

    Double score() {
        return score;
    }

    Instant scoredAt() {
        return scoredAt;
    }

    boolean degraded() {
        return degraded;
    }
}
