package com.fraudguard.kafka;

import java.time.Instant;
import java.util.List;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.Factor;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FraudDecisionEventTest {

    @Test
    void maps_a_scored_decision_to_the_wire_event() {
        // WHY: the wire contract is a promise to consumers (the dashboard). A silent field-mapping
        // change here breaks every downstream reader, so pin each field explicitly.
        Transaction txn = Transactions.usd("50.00");
        RiskFactor velocity = new RiskFactor(ReasonCode.VELOCITY_1M, "4 in the last minute", 1.0, 0.45);
        Instant decidedAt = Instant.parse("2026-06-08T10:15:30Z");
        FraudDecision decision = new FraudDecision(Decision.BLOCK, 0.45, List.of(velocity), decidedAt, false);

        FraudDecisionEvent event = FraudDecisionEvent.of(txn, decision, "evt_1");

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.transactionId()).isEqualTo(txn.id());
        assertThat(event.accountId()).isEqualTo(txn.accountId());
        assertThat(event.decision()).isEqualTo(Decision.BLOCK);
        assertThat(event.score()).isEqualTo(0.45);
        assertThat(event.degraded()).isFalse();
        assertThat(event.decidedAt()).isEqualTo(decidedAt);
        // contribution = severity * weight = 1.0 * 0.45; severity/weight are intentionally dropped.
        assertThat(event.factors()).containsExactly(
                new Factor(ReasonCode.VELOCITY_1M, "4 in the last minute", 0.45));
    }

    @Test
    void degraded_decision_has_null_score_so_NaN_never_reaches_the_wire() {
        // WHY: FraudDecision stores Double.NaN for a degraded decision and JSON has no NaN; the
        // contract promises consumers null, not a token that would fail to serialize or parse.
        Transaction txn = Transactions.usd("50.00");
        FraudDecision degraded = FraudDecision.degradedReview(Instant.parse("2026-06-08T10:15:30Z"));

        FraudDecisionEvent event = FraudDecisionEvent.of(txn, degraded, "evt_2");

        assertThat(event.degraded()).isTrue();
        assertThat(event.score()).isNull();
        assertThat(event.decision()).isEqualTo(Decision.REVIEW);
        assertThat(event.factors()).hasSize(1);
        assertThat(event.factors().get(0).code()).isEqualTo(ReasonCode.SCORING_UNAVAILABLE);
    }
}
