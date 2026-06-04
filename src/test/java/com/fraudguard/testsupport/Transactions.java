package com.fraudguard.testsupport;

import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;

import java.time.Instant;

/**
 * Test fixtures for {@link Transaction}. Defaults model a mundane US purchase; tests override
 * only the fields the rule under test cares about.
 */
public final class Transactions {

    private Transactions() {
    }

    /** A default $50 USD transaction from the US on a known device. */
    public static Transaction usd(String amount) {
        return usd(amount, "US");
    }

    /** A $-amount USD transaction with an explicit transaction-country. */
    public static Transaction usd(String amount, String geoCountry) {
        return new Transaction(
                "tx_test",
                "idem_test",
                "acct_1",
                "card_fp_1",
                Money.of(amount, "USD"),
                "Test Merchant",
                "retail",
                "203.0.113.7",
                geoCountry,
                "US",
                "device_known_1",
                Instant.parse("2026-06-04T09:42:15Z"));
    }
}
