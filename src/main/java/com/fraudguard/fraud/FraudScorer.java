package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Transaction;

/**
 * Produces a {@link FraudDecision} for a transaction given its feature snapshot.
 *
 * <p>This is the seam that lets scoring be swapped or composed without touching callers:
 * today the only implementation is {@code RuleBasedScorer}; a machine-learning scorer and a
 * composite (rules + ML) arrive in a later milestone behind this same interface.
 */
public interface FraudScorer {

    FraudDecision score(Transaction transaction, FeatureSnapshot features);
}
