package com.fraudguard.payments.persistence;

import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
class TransactionDetails {

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "card_fingerprint", nullable = false)
    private String cardFingerprint;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String merchant;

    @Column(name = "merchant_category", nullable = false)
    private String merchantCategory;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "geo_country", nullable = false, length = 2)
    private String geoCountry;

    @Column(name = "billing_country", nullable = false, length = 2)
    private String billingCountry;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionDetails() {
    }

    private TransactionDetails(Transaction transaction) {
        accountId = transaction.accountId();
        cardFingerprint = transaction.cardFingerprint();
        amount = transaction.amount().amount();
        currency = transaction.amount().currency().getCurrencyCode();
        merchant = transaction.merchant();
        merchantCategory = transaction.merchantCategory();
        ipAddress = transaction.ipAddress();
        geoCountry = transaction.geoCountry();
        billingCountry = transaction.billingCountry();
        deviceId = transaction.deviceId();
        createdAt = transaction.createdAt();
    }

    static TransactionDetails from(Transaction transaction) {
        return new TransactionDetails(transaction);
    }

    Transaction toDomain(String id, String idempotencyKey) {
        return new Transaction(
                id,
                idempotencyKey,
                accountId,
                cardFingerprint,
                Money.of(amount, currency),
                merchant,
                merchantCategory,
                ipAddress,
                geoCountry,
                billingCountry,
                deviceId,
                createdAt);
    }

    String accountId() {
        return accountId;
    }

    String deviceId() {
        return deviceId;
    }
}
