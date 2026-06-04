package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Transaction;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entry point the rest of the app calls to score a transaction. Its job beyond delegating to
 * the {@link FraudScorer} is the degradation guarantee from the eng review: if scoring throws
 * (e.g. the feature store is unreachable), never silently approve — fall back to a loud
 * {@link Decision#REVIEW}.
 *
 * <p>This is the one place that decides what happens when the engine itself fails, so it is the
 * one place that must be impossible to get wrong silently.
 */
public final class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final FraudScorer scorer;
    private final Clock clock;

    public ScoringService(FraudScorer scorer, Clock clock) {
        this.scorer = scorer;
        this.clock = clock;
    }

    public FraudDecision decide(Transaction transaction, FeatureSnapshot features) {
        try {
            return scorer.score(transaction, features);
        } catch (RuntimeException e) {
            log.error("Scoring failed for transaction {}; failing to REVIEW", transaction.id(), e);
            return FraudDecision.degradedReview(clock.instant());
        }
    }
}
