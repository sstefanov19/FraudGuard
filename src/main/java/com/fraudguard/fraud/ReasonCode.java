package com.fraudguard.fraud;

/**
 * Stable, machine-readable identifiers for why a transaction scored the way it did.
 *
 * <p>These are part of the API contract and the audit trail: the breakdown screen renders them,
 * analysts filter on them, and they must not change meaning once shipped. The human-readable
 * sentence shown to a user is built by the rule at evaluation time (with the concrete numbers);
 * the code is the durable key.
 */
public enum ReasonCode {
    VELOCITY_1M,
    VELOCITY_1H,
    AMOUNT_ANOMALY,
    GEO_COUNTRY_MISMATCH,
    NEW_DEVICE_HIGH_VALUE,
    BLOCKLIST_HIT,
    SCORING_UNAVAILABLE
}
