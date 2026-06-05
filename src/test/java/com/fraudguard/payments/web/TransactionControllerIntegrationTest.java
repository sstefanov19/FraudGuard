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
import org.springframework.http.MediaType;
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

    @Test
    void amount_with_more_decimals_than_the_currency_allows_is_rejected_as_400_without_persisting() {
        // WHY: 50.999 USD would make Money's RoundingMode.UNNECESSARY throw; that must surface as a
        // client 400, not a 500, and must not leave a half-written transaction row behind.
        Map<String, Object> body = request("acct_overscale", "device_overscale", "50.999");

        ResponseEntity<String> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("idem_overscale")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void bean_validation_failure_returns_the_same_error_contract_as_the_amount_check() {
        // WHY: the API must speak one error dialect; a bad request body and an over-precise amount
        // both come back as the ErrorResponse shape, not Spring's default error JSON.
        Map<String, Object> body = request("acct_invalid", "device_invalid", "50.00");
        body.put("currency", "US"); // fails @Pattern("[A-Z]{3}")

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("idem_invalid")),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("currency");
        assertThat(transactions.count()).isZero();
    }

    @Test
    void unsupported_currency_code_is_rejected_as_400_not_500() {
        // WHY: "ZZZ" passes the [A-Z]{3} pattern but Currency.getInstance rejects it; that must come
        // back as a client 400, not an IllegalArgumentException surfacing as a 500.
        Map<String, Object> body = request("acct_badccy", "device_badccy", "50.00");
        body.put("currency", "ZZZ");

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("idem_badccy")),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("ZZZ");
        assertThat(transactions.count()).isZero();
    }

    @Test
    void non_usd_currency_is_rejected_as_400_not_silently_degraded() {
        // WHY: the wired rules use a fixed-USD threshold, so a non-USD amount throws on cross-currency
        // comparison and the engine degrades to a fake REVIEW. The API must reject it up front as 400.
        Map<String, Object> body = request("acct_eur", "device_eur", "50.00");
        body.put("currency", "EUR");

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("idem_eur")),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("EUR");
        assertThat(transactions.count()).isZero();
    }

    @Test
    void missing_idempotency_key_header_returns_the_error_contract() {
        // WHY: omitting the header raises MissingRequestHeaderException before @NotBlank runs; the
        // client must still get the ErrorResponse shape, not Spring's default error body.
        Map<String, Object> body = request("acct_nohdr", "device_nohdr", "50.00");

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions", new HttpEntity<>(body), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void malformed_json_body_returns_the_error_contract() {
        // WHY: an unreadable body raises HttpMessageNotReadableException; it must map to the same
        // 400 ErrorResponse dialect as every other client-input error.
        HttpHeaders headers = headers("idem_malformed");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions", new HttpEntity<>("{not valid json", headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void oversized_field_returns_400_not_500_when_it_exceeds_the_column_limit() {
        // WHY: a value longer than its DB column (account_id VARCHAR(128)) must be rejected as a
        // client 400, not blow up as a DataIntegrityViolationException surfacing as a 500.
        Map<String, Object> body = request("a".repeat(129), "device_oversized", "50.00");

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("idem_oversized")),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("accountId");
        assertThat(transactions.count()).isZero();
    }

    @Test
    void oversized_idempotency_key_header_returns_400_via_the_same_error_contract() {
        // WHY: the Idempotency-Key header has its own column limit (VARCHAR(255)); an over-long key
        // must fail as a 400 ErrorResponse, the same dialect as body validation, not a 500.
        Map<String, Object> body = request("acct_idem_long", "device_idem_long", "50.00");

        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/transactions",
                new HttpEntity<>(body, headers("k".repeat(256))),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(transactions.count()).isZero();
    }

    private HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
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
