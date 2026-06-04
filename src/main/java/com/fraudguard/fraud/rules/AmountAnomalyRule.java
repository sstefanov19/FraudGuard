package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;

import java.util.Optional;

/**
 * Fires when a transaction is far larger than the account's normal spend — a stolen-card or
 * bust-out signal. Compares the amount against the account's trailing average.
 *
 * <p>Needs history to mean anything: with fewer than {@code minHistory} prior transactions there
 * is no reliable baseline, so the rule stays silent (a first-ever large purchase is handled by
 * other signals like new-device, not by a baseline that doesn't exist yet).
 *
 * <p>Severity scales from the moment the amount exceeds {@code multiple} × average, reaching 1.0
 * at {@code 2 × multiple} × average.
 */
public final class AmountAnomalyRule implements Rule {

    private final double multiple;
    private final int minHistory;
    private final double weight;

    public AmountAnomalyRule(double multiple, int minHistory, double weight) {
        if (multiple <= 1.0 || minHistory < 1) {
            throw new IllegalArgumentException("multiple must be > 1 and minHistory >= 1");
        }
        this.multiple = multiple;
        this.minHistory = minHistory;
        this.weight = weight;
    }

    @Override
    public Optional<RiskFactor> evaluate(Transaction transaction, FeatureSnapshot features) {
        if (!features.hasHistory() || features.priorTransactionCount() < minHistory) {
            return Optional.empty();
        }
        Money average = features.trailingAverageAmount();
        Money threshold = average.times(multiple);
        if (!transaction.amount().isGreaterThan(threshold)) {
            return Optional.empty();
        }
        // How many "multiples of average" over: ratio = amount / average. Severity maps
        // [multiple .. 2*multiple] of the average onto [0 .. 1].
        double ratio = transaction.amount().amount().doubleValue() / average.amount().doubleValue();
        double severity = Math.min(1.0, (ratio - multiple) / multiple);
        String description = "Amount %s is %.1fx the account's trailing average of %s"
                .formatted(transaction.amount(), ratio, average);
        return Optional.of(new RiskFactor(ReasonCode.AMOUNT_ANOMALY, description, severity, weight));
    }
}
