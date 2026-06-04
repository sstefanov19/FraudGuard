package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FeatureSnapshotBuilder;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * New device AND high value together are an account-takeover signal. Either alone is innocent,
 * so the rule requires both.
 */
class NewDeviceHighValueRuleTest {

    private final NewDeviceHighValueRule rule = new NewDeviceHighValueRule(Money.of("1000.00", "USD"), 0.10);

    @Test
    void fires_only_when_a_new_device_makes_a_high_value_charge() {
        // WHY: the combination is the signal — a takeover often means a new device draining funds fast.
        Transaction bigCharge = Transactions.usd("1500.00");
        FeatureSnapshot newDevice = new FeatureSnapshotBuilder().newDevice(true).build();

        assertThat(rule.evaluate(bigCharge, newDevice)).isPresent();
        assertThat(rule.evaluate(bigCharge, newDevice).get().code()).isEqualTo(ReasonCode.NEW_DEVICE_HIGH_VALUE);
    }

    @Test
    void stays_silent_for_a_new_device_making_a_small_charge() {
        // WHY: buying a coffee from a new phone is normal; only the high-value combo is suspicious.
        Transaction smallCharge = Transactions.usd("12.00");
        FeatureSnapshot newDevice = new FeatureSnapshotBuilder().newDevice(true).build();

        assertThat(rule.evaluate(smallCharge, newDevice)).isEmpty();
    }

    @Test
    void stays_silent_for_a_high_value_charge_on_a_known_device() {
        // WHY: a big purchase from your usual phone is just a big purchase.
        Transaction bigCharge = Transactions.usd("1500.00");
        FeatureSnapshot knownDevice = new FeatureSnapshotBuilder().newDevice(false).build();

        assertThat(rule.evaluate(bigCharge, knownDevice)).isEmpty();
    }
}
