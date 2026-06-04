package com.fraudguard.payments.domain;

import java.util.Set;

/**
 * Transaction lifecycle state machine.
 *
 * <pre>
 *   RECEIVED ──score──▶ SCORED ──apply decision──▶ APPROVED
 *                                              ├──▶ BLOCKED
 *                                              └──▶ IN_REVIEW ──analyst──▶ APPROVED | BLOCKED
 * </pre>
 *
 * BLOCKED is terminal from scoring. IN_REVIEW is resolved by an analyst action (later milestone).
 */
public enum TransactionStatus {
    RECEIVED,
    SCORED,
    APPROVED,
    BLOCKED,
    IN_REVIEW;

    private static final java.util.Map<TransactionStatus, Set<TransactionStatus>> ALLOWED = java.util.Map.of(
            RECEIVED, Set.of(SCORED),
            SCORED, Set.of(APPROVED, BLOCKED, IN_REVIEW),
            IN_REVIEW, Set.of(APPROVED, BLOCKED),
            APPROVED, Set.of(),
            BLOCKED, Set.of()
    );

    public boolean canTransitionTo(TransactionStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
