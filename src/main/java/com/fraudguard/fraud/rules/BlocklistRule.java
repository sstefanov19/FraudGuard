package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;

/**
 * Fires when the card fingerprint, IP, or device is on a known-bad blocklist. This is a hard
 * signal: full severity and full weight, so a blocklist hit alone drives the score to BLOCK.
 */
public final class BlocklistRule implements Rule {

    @Override
    public Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features) {
        if (!features.anyBlocklistHit()) {
            return Optional.empty();
        }
        String which = (features.cardOnBlocklist() ? "card " : "")
                + (features.ipOnBlocklist() ? "ip " : "")
                + (features.deviceOnBlocklist() ? "device" : "");
        String description = "Blocklisted identifier: " + which.trim();
        return Optional.of(new RiskFactor(ReasonCode.BLOCKLIST_HIT, description, 1.0, 1.0));
    }
}
