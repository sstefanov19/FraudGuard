package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;

/**
 * One fraud heuristic. A rule is a pure function of the transaction and its feature snapshot:
 * it either fires (returning a {@link RiskFactor} explaining why and how strongly) or it doesn't
 * (empty).
 *
 * <p>One class per rule is deliberate: each is independently unit-testable, its threshold/weight
 * is injectable config, and the scorer just sums whatever fires. Adding a rule never edits an
 * existing one.
 */
public interface Rule {

    Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features);
}
