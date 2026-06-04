package com.fraudguard.payments.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money must behave like money: exact decimals, scale-insensitive equality, and a refusal to
 * compare across currencies. These are the properties a floating-point amount would silently break.
 */
class MoneyTest {

    @Test
    void treats_same_value_at_different_scales_as_equal() {
        // WHY: $10 and $10.00 are the same money; a ledger that disagrees corrupts balances.
        assertThat(Money.of("10", "USD")).isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void normalizes_to_the_currency_fraction_digits() {
        // WHY: USD has 2 fraction digits; storing at a fixed scale is what keeps equality and
        // comparison exact instead of drifting like floating point.
        assertThat(Money.of("10", "USD").amount().scale()).isEqualTo(2);
        assertThat(Money.of("10.00", "USD").amount().toPlainString()).isEqualTo("10.00");
    }

    @Test
    void comparing_across_currencies_fails_loudly() {
        // WHY: comparing USD to EUR is meaningless; better to throw than to return a wrong answer.
        assertThatThrownBy(() -> Money.of("10.00", "USD").isGreaterThan(Money.of("10.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void greater_than_compares_magnitude_within_a_currency() {
        assertThat(Money.of("100.00", "USD").isGreaterThan(Money.of("99.99", "USD"))).isTrue();
        assertThat(Money.of("99.99", "USD").isGreaterThan(Money.of("100.00", "USD"))).isFalse();
    }
}
