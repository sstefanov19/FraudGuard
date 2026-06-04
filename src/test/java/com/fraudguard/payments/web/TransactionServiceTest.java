package com.fraudguard.payments.web;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.ScoringService;
import com.fraudguard.fraud.features.FeatureProvider;
import com.fraudguard.payments.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-04T09:42:15Z"), ZoneOffset.UTC);

    @Test
    void feature_provider_failure_degrades_to_review_without_calling_scorer() {
        // WHY: the fail-to-REVIEW boundary must cover feature lookup too; a database outage must
        // not approve blind just because the scorer itself was never reached.
        TransactionRepository transactions = mock(TransactionRepository.class);
        FeatureProvider featureProvider = mock(FeatureProvider.class);
        ScoringService scoringService = mock(ScoringService.class);
        when(transactions.findByIdempotencyKey("idem_feature_down")).thenReturn(Optional.empty());
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(featureProvider.load(any())).thenThrow(new RuntimeException("database unavailable"));
        TransactionService service = new TransactionService(
                transactions, featureProvider, scoringService, clock, immediateTransactions());

        TransactionResponse response = service.create("idem_feature_down", request());

        assertThat(response.decision()).isEqualTo(Decision.REVIEW);
        assertThat(response.degraded()).isTrue();
        assertThat(response.score()).isNull();
        verifyNoInteractions(scoringService);
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };
    }

    private CreateTransactionRequest request() {
        return new CreateTransactionRequest(
                "acct_1",
                "card_fp_1",
                new BigDecimal("50.00"),
                "USD",
                "Test Merchant",
                "retail",
                "203.0.113.7",
                "US",
                "US",
                "device_1");
    }
}
