package com.fraudguard.payments.web;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.fraud.ScoringService;
import com.fraudguard.fraud.features.FeatureProvider;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionEntity;
import com.fraudguard.payments.persistence.TransactionRepository;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public TransactionService(
            TransactionRepository transactions,
            FeatureProvider featureProvider,
            ScoringService scoringService,
            Clock clock,
            TransactionOperations transactionOperations) {
        this.transactions = transactions;
        this.featureProvider = featureProvider;
        this.scoringService = scoringService;
        this.clock = clock;
        this.transactionOperations = transactionOperations;
    }

    public TransactionResponse create(String idempotencyKey, CreateTransactionRequest request) {
        return transactions.findByIdempotencyKey(idempotencyKey)
                .map(TransactionResponse::fromStored)
                .orElseGet(() -> createNew(idempotencyKey, request));
    }

    private TransactionResponse createNew(String idempotencyKey, CreateTransactionRequest request) {
        try {
            return transactionOperations.execute(status -> persistScoreAndRespond(idempotencyKey, request));
        } catch (DataIntegrityViolationException e) {
            return transactions.findByIdempotencyKey(idempotencyKey)
                    .map(TransactionResponse::fromStored)
                    .orElseThrow(() -> e);
        }
    }

    private TransactionResponse persistScoreAndRespond(String idempotencyKey, CreateTransactionRequest request) {
        Transaction transaction = toDomain(idempotencyKey, request);
        TransactionEntity entity = TransactionEntity.fromDomain(transaction);
        transactions.saveAndFlush(entity);

        FraudDecision decision = decide(transaction);
        entity.applyDecision(decision);
        transactions.save(entity);
        return TransactionResponse.from(transaction.id(), decision);
    }

    private FraudDecision decide(Transaction transaction) {
        try {
            FeatureSnapshot features = featureProvider.load(transaction);
            return scoringService.decide(transaction, features);
        } catch (RuntimeException e) {
            log.error("Feature loading failed for transaction {}; failing to REVIEW", transaction.id(), e);
            return FraudDecision.degradedReview(clock.instant());
        }
    }

    private Transaction toDomain(String idempotencyKey, CreateTransactionRequest request) {
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
}
