package com.fraudguard.payments.web;

import com.fraudguard.fraud.Decision;
import com.fraudguard.payments.persistence.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionControllerIntegrationTest {

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

    @Test
    void fourth_small_charge_for_the_same_account_blocks_over_real_persistence() {
        // WHY: this is the headline fraud guarantee after wiring real storage: persist-then-score
        // means the 4th authorization is counted as the 4th event in the one-minute window.
        TransactionResponse response = null;
        for (int i = 1; i <= 4; i++) {
            response = post("idem_burst_" + i, request("acct_burst", "device_" + i, "1.00"));
        }

        assertThat(response.decision()).isEqualTo(Decision.BLOCK);
        assertThat(response.score()).isEqualTo(1.0);
        assertThat(response.factors()).anyMatch(factor -> factor.code().name().equals("VELOCITY_1M"));
    }

    @Test
    void mundane_purchase_approves() {
        // WHY: the live feature defaults must not manufacture risk for a normal domestic purchase
        // with no velocity, no blocklist data, and no account-country table yet.
        TransactionResponse response = post("idem_mundane", request("acct_mundane", "device_known", "50.00"));

        assertThat(response.decision()).isEqualTo(Decision.APPROVE);
        assertThat(response.score()).isEqualTo(0.0);
        assertThat(response.factors()).isEmpty();
    }

    @Test
    void idempotency_key_returns_stored_decision_without_creating_a_duplicate_row() {
        // WHY: payment clients retry on network uncertainty; retries must be deduped by key,
        // not charged and scored as fresh authorizations.
        Map<String, Object> body = request("acct_idem", "device_idem", "50.00");

        TransactionResponse first = post("idem_repeat", body);
        TransactionResponse second = post("idem_repeat", body);

        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(second.decision()).isEqualTo(first.decision());
        assertThat(second.score()).isEqualTo(first.score());
        assertThat(transactions.count()).isEqualTo(1);
    }

    private TransactionResponse post(String idempotencyKey, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<TransactionResponse> response = rest.postForEntity(
                "/transactions", new HttpEntity<>(request, headers), TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Map<String, Object> request(String accountId, String deviceId, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("cardFingerprint", "card_" + accountId);
        body.put("amount", new BigDecimal(amount));
        body.put("currency", "USD");
        body.put("merchant", "Test Merchant");
        body.put("merchantCategory", "retail");
        body.put("ipAddress", "203.0.113.7");
        body.put("geoCountry", "US");
        body.put("billingCountry", "US");
        body.put("deviceId", deviceId);
        return body;
    }
}
