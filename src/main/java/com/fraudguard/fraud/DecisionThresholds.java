package com.fraudguard.fraud;

/**
 * The score cut-offs that turn a numeric risk score into a {@link Decision}.
 *
 * <pre>
 *   0.0 ───────────── approveBelow ───────────── blockAtOrAbove ───────────── 1.0
 *        APPROVE                     REVIEW                       BLOCK
 * </pre>
 *
 * Defaults: APPROVE &lt; 0.40, REVIEW in [0.40, 0.80), BLOCK ≥ 0.80. These are the knobs the
 * Fraud Lab tuning console adjusts in a later milestone, which is why they are data, not magic
 * numbers buried in the scorer.
 */
public record DecisionThresholds(double approveBelow, double blockAtOrAbove) {

    public DecisionThresholds {
        if (!(approveBelow > 0.0 && approveBelow < blockAtOrAbove && blockAtOrAbove < 1.0)) {
            throw new IllegalArgumentException(
                    "Require 0 < approveBelow (%s) < blockAtOrAbove (%s) < 1".formatted(approveBelow, blockAtOrAbove));
        }
    }

    public static DecisionThresholds defaults() {
        return new DecisionThresholds(0.40, 0.80);
    }

    public Decision decisionFor(double score) {
        if (score < approveBelow) {
            return Decision.APPROVE;
        }
        if (score < blockAtOrAbove) {
            return Decision.REVIEW;
        }
        return Decision.BLOCK;
    }
}
