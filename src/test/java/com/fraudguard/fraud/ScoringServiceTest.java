package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The degradation guarantee (eng-review decision 4A): if the scorer throws — feature store down,
 * any unexpected error — the service must fail to REVIEW, loudly, never silently APPROVE.
 *
 * This is the single most important safety test in the engine: it's what stops an outage from
 * becoming a fraud free-for-all OR a total payment outage.
 */
class ScoringServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-04T09:42:15Z"), ZoneOffset.UTC);
    private final Transaction txn = Transactions.usd("100.00");
    private final FeatureSnapshot anyFeatures = new FeatureSnapshotBuilder().build();

    @Test
    void when_scoring_throws_it_fails_to_review_not_approve() {
        // WHY: a feature-store outage must NOT let unscored transactions through. The only safe
        // default is to hold for review — never approve blind.
        FraudScorer exploding = mock(FraudScorer.class);
        when(exploding.score(txn, anyFeatures)).thenThrow(new RuntimeException("feature store unreachable"));
        ScoringService service = new ScoringService(exploding, clock);

        FraudDecision decision = service.decide(txn, anyFeatures);

        assertThat(decision.decision()).isEqualTo(Decision.REVIEW);
        assertThat(decision.degraded()).isTrue();
        assertThat(decision.factors()).anyMatch(f -> f.code() == ReasonCode.SCORING_UNAVAILABLE);
    }

    @Test
    void when_scoring_succeeds_it_returns_the_scorer_result_unchanged() {
        // WHY: in the happy path the service must not alter the decision — it only guards failures.
        FraudScorer healthy = mock(FraudScorer.class);
        FraudDecision approved = new FraudDecision(Decision.APPROVE, 0.0, java.util.List.of(), clock.instant(), false);
        when(healthy.score(txn, anyFeatures)).thenReturn(approved);
        ScoringService service = new ScoringService(healthy, clock);

        assertThat(service.decide(txn, anyFeatures)).isSameAs(approved);
    }
}
