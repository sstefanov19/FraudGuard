package com.fraudguard.payments.domain;

/**
 * A payment method (card) belonging to an account.
 *
 * <p>{@code fingerprint} is a stable hash of the card (never the PAN) used for velocity and
 * blocklist lookups. {@code bin} (first 6-8 digits) and {@code issuingCountry} feed geo signals
 * such as issuing-country vs billing-country mismatch.
 */
public record Card(
        String id,
        String accountId,
        String fingerprint,
        String bin,
        String issuingCountry) {
}
