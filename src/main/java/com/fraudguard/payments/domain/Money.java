package com.fraudguard.payments.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount with an explicit currency.
 *
 * <p>Money is {@link BigDecimal}-backed, never floating point: {@code 0.1 + 0.2} must
 * equal {@code 0.3} and rounding must not drift across a ledger. The amount is normalized
 * to the currency's default fraction digits (USD -> 2) so equality and comparison are exact.
 *
 * <p>Comparisons across different currencies throw — you cannot meaningfully compare USD to EUR.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        // Normalize scale to the currency's fraction digits so 10 and 10.00 are equal.
        amount = amount.setScale(currency.getDefaultFractionDigits(), java.math.RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /** Multiply by a scalar (e.g. an anomaly multiplier). Result keeps this currency's scale. */
    public Money times(double factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot compare across currencies: %s vs %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount.toPlainString(), currency.getCurrencyCode());
    }
}
