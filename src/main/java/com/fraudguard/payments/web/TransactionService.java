package com.fraudguard.payments.web;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.fraud.ScoringService;
import com.fraudguard.fraud.features.FeatureProvider;
import com.fraudguard.kafka.FraudDecisionRecorded;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionEntity;
import com.fraudguard.payments.persistence.TransactionRepository;

import java.time.Clock;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactions;
    private final FeatureProvider featureProvider;
    private final ScoringService scoringService;
    private final Clock clock;
    private final TransactionOperations transactionOperations;
    private final ApplicationEventPublisher events;
    private final Set<String> supportedCurrencies;

    public TransactionService(
            TransactionRepository transactions,
            FeatureProvider featureProvider,
            ScoringService scoringService,
            Clock clock,
            TransactionOperations transactionOperations,
            ApplicationEventPublisher events,
            @Value("${fraud.supported-currencies:USD}") Set<String> supportedCurrencies) {
        this.transactions = transactions;
        this.featureProvider = featureProvider;
        this.scoringService = scoringService;
        this.clock = clock;
        this.transactionOperations = transactionOperations;
        this.events = events;
        this.supportedCurrencies = supportedCurrencies.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public TransactionResponse create(String idempotencyKey, CreateTransactionRequest request) {
        return transactions.findByIdempotencyKey(idempotencyKey)
                .map(TransactionResponse::fromStored)
                .orElseGet(() -> createNew(idempotencyKey, request));
    }

    private TransactionResponse createNew(String idempotencyKey, CreateTransactionRequest request) {
        Transaction transaction = toDomain(idempotencyKey, request); // validation 400s happen before any tx
        try {
            return transactionOperations.execute(status -> scoreInTransaction(transaction));
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert won the unique idempotency key — return the stored decision.
            return transactions.findByIdempotencyKey(idempotencyKey)
                    .map(TransactionResponse::fromStored)
                    .orElseThrow(() -> e);
        } catch (FeatureLoadException e) {
            // Feature reads touch the DB; a failure there aborts the write transaction (now rolled
            // back). Honor the fail-to-REVIEW guarantee by persisting a degraded REVIEW in a fresh
            // transaction rather than letting the poisoned one surface as a 500.
            log.error("Feature loading failed for transaction {}; failing to REVIEW", transaction.id(), e.getCause());
            return persistDegradedReview(transaction);
        }
    }

    private TransactionResponse scoreInTransaction(Transaction transaction) {
        TransactionEntity entity = TransactionEntity.fromDomain(transaction);
        transactions.saveAndFlush(entity);

        FeatureSnapshot features = loadFeatures(transaction);
        FraudDecision decision = scoringService.decide(transaction, features);
        entity.applyDecision(decision);
        transactions.save(entity);
        // Registered inside the txn; AFTER_COMMIT publishes it only once this commits.
        events.publishEvent(new FraudDecisionRecorded(transaction, decision));
        return TransactionResponse.from(transaction.id(), decision);
    }

    private FeatureSnapshot loadFeatures(Transaction transaction) {
        try {
            return featureProvider.load(transaction);
        } catch (RuntimeException e) {
            // Rethrow so the surrounding transaction rolls back instead of trying to commit on a
            // connection the failed read may have already aborted. createNew then persists the
            // degraded REVIEW in a clean transaction.
            throw new FeatureLoadException(e);
        }
    }

    private TransactionResponse persistDegradedReview(Transaction transaction) {
        return transactionOperations.execute(status -> {
            TransactionEntity entity = TransactionEntity.fromDomain(transaction);
            FraudDecision decision = FraudDecision.degradedReview(clock.instant());
            entity.applyDecision(decision);
            transactions.saveAndFlush(entity);
            events.publishEvent(new FraudDecisionRecorded(transaction, decision));
            return TransactionResponse.from(transaction.id(), decision);
        });
    }

    private Transaction toDomain(String idempotencyKey, CreateTransactionRequest request) {
        requireSupportedMoney(request);
        return new Transaction(
                "tx_" + UUID.randomUUID(),
                idempotencyKey,
                request.accountId(),
                request.cardFingerprint(),
                Money.of(request.amount(), request.currency()),
                request.merchant(),
                request.merchantCategory(),
                request.ipAddress(),
                request.geoCountry(),
                request.billingCountry(),
                request.deviceId(),
                clock.instant());
    }

    /**
     * Reject money values the engine cannot honor with a 400 instead of a 500 (or a fake "scoring
     * outage"): a currency outside {@code supportedCurrencies} (the wired rules use a fixed-USD
     * threshold, so a non-USD amount would throw on cross-currency comparison and degrade to REVIEW),
     * a non-ISO currency code (passes the [A-Z]{3} pattern but is not real, e.g. "ZZZ"), or more
     * fraction digits than the currency supports (e.g. 50.999 USD). Trailing zeros are stripped first
     * so 50.990 USD stays valid.
     */
    private void requireSupportedMoney(CreateTransactionRequest request) {
        if (!supportedCurrencies.contains(request.currency())) {
            throw new InvalidMoneyException(
                    "unsupported currency " + request.currency() + "; supported: " + supportedCurrencies);
        }
        Currency currency;
        try {
            currency = Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            throw new InvalidMoneyException("unsupported currency " + request.currency());
        }
        if (request.amount().stripTrailingZeros().scale() > currency.getDefaultFractionDigits()) {
            throw new InvalidMoneyException(
                    "amount has more decimal places than %s allows".formatted(currency.getCurrencyCode()));
        }
    }

    /** Internal marker so a feature-read failure rolls back the write transaction (see loadFeatures). */
    private static final class FeatureLoadException extends RuntimeException {
        FeatureLoadException(Throwable cause) {
            super(cause);
        }
    }
}
