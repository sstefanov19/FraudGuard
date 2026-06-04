package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FeatureSnapshotBuilder;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Velocity is the card-testing signal: a stolen card gets validated by a burst of tiny charges.
 * The 1-minute rule here mirrors the production config (threshold 3, full severity one over).
 */
class VelocityRuleTest {

    private final VelocityRule oneMinuteRule = new VelocityRule(
            ReasonCode.VELOCITY_1M, 3, 1, 1.00, FeatureSnapshot::txnCountLastMinute, "minute");

    private final Transaction smallCharge = Transactions.usd("1.00");

    @Test
    void the_fourth_charge_in_a_minute_fires_at_full_severity() {
        // WHY: 4 charges in 60s from one card is the textbook card-testing burst — it must be
        // caught hard, not nudged. One over the threshold of 3 = maximum severity by design.
        FeatureSnapshot fourInAMinute = new FeatureSnapshotBuilder().txnCountLastMinute(4).build();

        Optional<RiskFactor> factor = oneMinuteRule.evaluate(smallCharge, fourInAMinute);

        assertThat(factor).isPresent();
        assertThat(factor.get().code()).isEqualTo(ReasonCode.VELOCITY_1M);
        assertThat(factor.get().severity()).isEqualTo(1.0);
        assertThat(factor.get().contribution()).isEqualTo(1.0); // severity 1.0 x weight 1.0
    }

    @Test
    void three_charges_in_a_minute_does_not_fire() {
        // WHY: normal people do buy a few things in a minute; the threshold exists so we don't
        // block them. At or below threshold the rule stays silent.
        FeatureSnapshot threeInAMinute = new FeatureSnapshotBuilder().txnCountLastMinute(3).build();

        assertThat(oneMinuteRule.evaluate(smallCharge, threeInAMinute)).isEmpty();
    }

    @Test
    void describes_the_actual_count_and_window_for_the_breakdown_screen() {
        // WHY: explainability — the analyst sees the real numbers, not a generic "velocity" label.
        FeatureSnapshot fiveInAMinute = new FeatureSnapshotBuilder().txnCountLastMinute(5).build();

        RiskFactor factor = oneMinuteRule.evaluate(smallCharge, fiveInAMinute).orElseThrow();

        assertThat(factor.description()).contains("5 transactions").contains("minute").contains("threshold 3");
    }
}
