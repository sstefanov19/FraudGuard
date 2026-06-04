package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;

/**
 * Fires when a high-value transaction comes from a device never seen on this account before.
 * Either signal alone is weak (people buy new phones; people make big purchases) but together
 * they are a meaningful account-takeover indicator.
 *
 * <p>Binary trigger, so severity is 1.0 when it fires; {@code weight} is small because it's a
 * supporting signal, not a standalone reason to block.
 */
public final class NewDeviceHighValueRule implements Rule {

    private final Money highValueThreshold;
    private final double weight;

    public NewDeviceHighValueRule(Money highValueThreshold, double weight) {
        this.highValueThreshold = highValueThreshold;
        this.weight = weight;
    }

    @Override
    public Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features) {
        boolean highValue = transaction.amount().isGreaterThan(highValueThreshold);
        if (!features.newDevice() || !highValue) {
            return Optional.empty();
        }
        String description = "First-seen device on a high-value charge (%s > %s)"
                .formatted(transaction.amount(), highValueThreshold);
        return Optional.of(new RiskFactor(ReasonCode.NEW_DEVICE_HIGH_VALUE, description, 1.0, weight));
    }
}
