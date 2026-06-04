package com.fraudguard.fraud;

import com.fraudguard.fraud.rules.Rule;
import com.fraudguard.payments.domain.Transaction;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Scores a transaction by running every {@link Rule}, collecting the {@link RiskFactor}s that
 * fire, and summing their contributions into a single risk score in [0,1]. The score maps to a
 * {@link Decision} via {@link DecisionThresholds}.
 *
 * <pre>
 *   score = clamp01( Σ factor.contribution() )      // contribution = severity × weight
 *   decision = thresholds.decisionFor(score)
 * </pre>
 *
 * The scorer is pure given its rules and a clock; all I/O (feature lookups) happens upstream and
 * arrives as the {@link FeatureSnapshot}.
 */
public final class RuleBasedScorer implements FraudScorer {

    private final List<Rule> rules;
    private final DecisionThresholds thresholds;
    private final Clock clock;

    public RuleBasedScorer(List<Rule> rules, DecisionThresholds thresholds, Clock clock) {
        this.rules = List.copyOf(rules);
        this.thresholds = thresholds;
        this.clock = clock;
    }

    @Override
    public FraudDecision score(Transaction transaction, FeatureSnapshot features) {
        List<RiskFactor> factors = rules.stream()
                .map(rule -> rule.evaluate(transaction, features))
                .flatMap(Optional::stream)
                .toList();

        double total = factors.stream().mapToDouble(RiskFactor::contribution).sum();
        double score = clampToUnitInterval(total);
        Decision decision = thresholds.decisionFor(score);

        return new FraudDecision(decision, score, factors, clock.instant(), false);
    }

    private static double clampToUnitInterval(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
