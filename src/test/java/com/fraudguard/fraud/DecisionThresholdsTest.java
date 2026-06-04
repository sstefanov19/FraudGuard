package com.fraudguard.fraud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The thresholds decide which band a score falls into. The boundaries are exact and inclusive
 * on the upper side (a score AT blockAtOrAbove blocks) — getting an edge wrong would mis-route
 * real money.
 */
class DecisionThresholdsTest {

    private final DecisionThresholds thresholds = DecisionThresholds.defaults(); // 0.40 / 0.80

    @Test
    void just_below_approve_cutoff_approves() {
        assertThat(thresholds.decisionFor(0.39)).isEqualTo(Decision.APPROVE);
    }

    @Test
    void exactly_at_approve_cutoff_is_review_not_approve() {
        // WHY: APPROVE is "below 0.40"; 0.40 itself is already borderline and must be reviewed.
        assertThat(thresholds.decisionFor(0.40)).isEqualTo(Decision.REVIEW);
    }

    @Test
    void just_below_block_cutoff_reviews() {
        assertThat(thresholds.decisionFor(0.79)).isEqualTo(Decision.REVIEW);
    }

    @Test
    void exactly_at_block_cutoff_blocks() {
        // WHY: BLOCK is "at or above 0.80"; the boundary itself must block, not slip through to review.
        assertThat(thresholds.decisionFor(0.80)).isEqualTo(Decision.BLOCK);
    }
}
