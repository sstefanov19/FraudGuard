package com.fraudguard.fraud;

/**
 * One reason a transaction looked risky — the atom of explainability.
 *
 * <p>A rule that fires emits exactly one RiskFactor. The breakdown screen renders the
 * {@code code} + {@code description} + {@code contribution}; the total risk score is the
 * (clamped) sum of all contributions.
 *
 * <ul>
 *   <li>{@code severity} ∈ [0,1] — how strongly THIS rule fired (0 = not at all, 1 = maximally).</li>
 *   <li>{@code weight}   ∈ [0,1] — the rule's configured importance (its maximum contribution).</li>
 *   <li>{@code contribution} = severity × weight — what this factor adds to the total score.</li>
 * </ul>
 *
 * Keeping severity and weight separate (rather than one opaque "points" number) is what makes
 * the score tunable later (the Fraud Lab adjusts weights) and the breakdown honest.
 */
public record RiskFactor(ReasonCode code, String description, double severity, double weight) {

    public RiskFactor {
        requireBetweenZeroAndOne("severity", severity);
        requireBetweenZeroAndOne("weight", weight);
    }

    public double contribution() {
        return severity * weight;
    }

    /** Guards that a 0-to-1 value (severity or weight) really is in range, failing loud if not. */
    private static void requireBetweenZeroAndOne(String fieldName, double value) {
        if (value < 0.0 || value > 1.0 || Double.isNaN(value)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1, was " + value);
        }
    }
}
