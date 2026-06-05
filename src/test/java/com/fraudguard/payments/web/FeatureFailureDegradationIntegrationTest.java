package com.fraudguard.payments.web;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.features.FeatureProvider;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FeatureFailureDegradationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TransactionRepository transactions;

    @BeforeEach
    void clearTransactions() {
        transactions.deleteAll();
    }

    /**
     * Replaces the real provider with one whose query fails against the live database, so the failure
     * aborts the write transaction the way a production DB error would — the case the mock-based unit
     * test cannot exercise.
     */
    @TestConfiguration
    static class FailingFeatureProviderConfig {

        @Bean
        @Primary
        FeatureProvider failingFeatureProvider(JdbcTemplate jdbc) {
            return new FeatureProvider() {
                @Override
                public FeatureSnapshot load(Transaction transaction) {
                    jdbc.queryForObject("select 1 from a_table_that_does_not_exist", Integer.class);
                    throw new IllegalStateException("unreachable");
                }
            };
        }
    }

    @Test
    void a_real_db_failure_during_feature_loading_still_persists_a_degraded_review() {
        // WHY: the flagship guarantee is "never silently approve — on a scoring/feature failure, fail
        // to REVIEW". The realistic trigger is a DB error during feature reads, which aborts the write
        // transaction. The request must still come back as a persisted, degraded REVIEW, not a 500.
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idem_dbfail");

        ResponseEntity<TransactionResponse> response = rest.postForEntity(
                "/transactions", new HttpEntity<>(request(), headers), TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(Decision.REVIEW);
        assertThat(response.getBody().degraded()).isTrue();
        assertThat(response.getBody().score()).isNull();
        assertThat(transactions.count()).isEqualTo(1);
    }

    private Map<String, Object> request() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", "acct_dbfail");
        body.put("cardFingerprint", "card_dbfail");
        body.put("amount", new BigDecimal("50.00"));
        body.put("currency", "USD");
        body.put("merchant", "Test Merchant");
        body.put("merchantCategory", "retail");
        body.put("ipAddress", "203.0.113.7");
        body.put("geoCountry", "US");
        body.put("billingCountry", "US");
        body.put("deviceId", "device_dbfail");
        return body;
    }
}
