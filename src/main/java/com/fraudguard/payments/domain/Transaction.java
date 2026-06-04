package com.fraudguard.payments.domain;

import java.time.Instant;

/**
 * An incoming payment authorization request — the unit the fraud engine scores.
 *
 * <p>This is immutable request data. The lifecycle {@link TransactionStatus} and the
 * {@code FraudDecision} are tracked separately by the persistence/controller layer
 * (build-order step 3); the scorer treats a Transaction as read-only input.
 *
 * <p>The dimensions here (device, merchant + category, card BIN/country, billing country)
 * exist because fraud signals need them: card-testing, account-takeover, and bust-out
 * patterns are invisible if all you have is an amount.
 */
public record Transaction(
        String id,
        String idempotencyKey,
        String accountId,
        String cardFingerprint,
        Money amount,
        String merchant,
        String merchantCategory,
        String ipAddress,
        String geoCountry,
        String billingCountry,
        String deviceId,
        Instant createdAt) {
}
