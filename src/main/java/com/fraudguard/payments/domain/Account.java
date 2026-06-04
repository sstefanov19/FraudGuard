package com.fraudguard.payments.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * A customer account. {@code homeCountry} is the ISO country the account is registered in;
 * the geo rule compares transaction country against it. {@code createdAt} drives account-age
 * signals (brand-new accounts transacting large amounts are riskier).
 */
public record Account(
        String id,
        String holderName,
        String homeCountry,
        Money balance,
        Instant createdAt) {

    public long ageDays(Instant asOf) {
        return Duration.between(createdAt, asOf).toDays();
    }
}
