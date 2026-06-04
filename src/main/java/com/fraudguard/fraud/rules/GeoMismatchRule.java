package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;

/**
 * Fires when the transaction's country differs from the account's home country — a possible
 * account-takeover or stolen-card signal. Binary: it either matches or it doesn't, so severity
 * is always 1.0 when it fires and the {@code weight} alone decides how much it contributes.
 *
 * <p>Weighted on its own to land in the REVIEW band, not BLOCK: a foreign transaction is often
 * just travel, so it warrants a look rather than an automatic rejection.
 */
public final class GeoMismatchRule implements Rule {

    private final double weight;

    public GeoMismatchRule(double weight) {
        this.weight = weight;
    }

    @Override
    public Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features) {
        String txnCountry = transaction.geoCountry();
        String homeCountry = features.accountHomeCountry();
        if (txnCountry == null || homeCountry == null || txnCountry.equalsIgnoreCase(homeCountry)) {
            return Optional.empty();
        }
        String description = "Transaction country %s differs from account home %s"
                .formatted(txnCountry, homeCountry);
        return Optional.of(new RiskFactor(ReasonCode.GEO_COUNTRY_MISMATCH, description, 1.0, weight));
    }
}
