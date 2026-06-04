package com.fraudguard.payments.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lifecycle must only allow legal transitions. The point of the state machine is that an
 * already-decided transaction can never be silently re-decided.
 */
class TransactionStatusTest {

    @Test
    void received_can_only_advance_to_scored() {
        // WHY: scoring must happen before any verdict; you can't approve a transaction you never scored.
        assertThat(TransactionStatus.RECEIVED.canTransitionTo(TransactionStatus.SCORED)).isTrue();
        assertThat(TransactionStatus.RECEIVED.canTransitionTo(TransactionStatus.APPROVED)).isFalse();
    }

    @Test
    void scored_can_become_any_verdict() {
        assertThat(TransactionStatus.SCORED.canTransitionTo(TransactionStatus.APPROVED)).isTrue();
        assertThat(TransactionStatus.SCORED.canTransitionTo(TransactionStatus.BLOCKED)).isTrue();
        assertThat(TransactionStatus.SCORED.canTransitionTo(TransactionStatus.IN_REVIEW)).isTrue();
    }

    @Test
    void in_review_is_resolved_by_an_analyst_to_a_terminal_verdict() {
        // WHY: a review item exists to be approved or blocked by a human; it shouldn't loop back.
        assertThat(TransactionStatus.IN_REVIEW.canTransitionTo(TransactionStatus.APPROVED)).isTrue();
        assertThat(TransactionStatus.IN_REVIEW.canTransitionTo(TransactionStatus.BLOCKED)).isTrue();
        assertThat(TransactionStatus.IN_REVIEW.canTransitionTo(TransactionStatus.SCORED)).isFalse();
    }

    @Test
    void terminal_verdicts_cannot_change() {
        // WHY: once blocked or approved, the decision is final and auditable; no silent reversal.
        assertThat(TransactionStatus.BLOCKED.canTransitionTo(TransactionStatus.APPROVED)).isFalse();
        assertThat(TransactionStatus.APPROVED.canTransitionTo(TransactionStatus.BLOCKED)).isFalse();
    }
}
