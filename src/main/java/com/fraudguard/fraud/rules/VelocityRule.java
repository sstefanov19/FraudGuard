package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Fires when too many transactions occur in a rolling window — the classic card-testing /
 * automated-abuse signal. One class serves any window; the window's count is supplied by a
 * {@code countExtractor} (e.g. {@code FeatureSnapshot::txnCountLastMinute}).
 *
 * <p>Severity ramps linearly once the count exceeds {@code threshold}, reaching the maximum
 * (1.0) at {@code threshold + overageForMaxSeverity}:
 *
 * <pre>
 *   count <= threshold                       -> does not fire
 *   count == threshold + 1                   -> severity = 1 / overageForMaxSeverity
 *   count >= threshold + overageForMaxSeverity -> severity = 1.0 (capped)
 * </pre>
 *
 * A tight 1-minute rule (small threshold, {@code overageForMaxSeverity = 1}, high weight) is what
 * makes "the 4th $1 charge in 60 seconds" reach BLOCK on its own.
 */
public final class VelocityRule implements Rule {

    private final ReasonCode code;
    private final int threshold;
    private final int overageForMaxSeverity;
    private final double weight;
    private final ToIntFunction<FeatureSnapshot> countExtractor;
    private final String windowLabel;

    public VelocityRule(
            ReasonCode code,
            int threshold,
            int overageForMaxSeverity,
            double weight,
            ToIntFunction<FeatureSnapshot> countExtractor,
            String windowLabel) {
        if (threshold < 1 || overageForMaxSeverity < 1) {
            throw new IllegalArgumentException("threshold and overageForMaxSeverity must be >= 1");
        }
        this.code = code;
        this.threshold = threshold;
        this.overageForMaxSeverity = overageForMaxSeverity;
        this.weight = weight;
        this.countExtractor = countExtractor;
        this.windowLabel = windowLabel;
    }

    @Override
    public Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features) {
        int count = countExtractor.applyAsInt(features);
        if (count <= threshold) {
            return Optional.empty();
        }
        int overage = count - threshold;
        double severity = Math.min(1.0, (double) overage / overageForMaxSeverity);
        String description = "%d transactions in the last %s (threshold %d)".formatted(count, windowLabel, threshold);
        return Optional.of(new RiskFactor(code, description, severity, weight));
    }
}
