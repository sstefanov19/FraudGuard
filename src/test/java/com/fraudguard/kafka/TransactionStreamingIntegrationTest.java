package com.fraudguard.kafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionRepository;
import com.fraudguard.payments.web.TransactionResponse;
import com.fraudguard.testsupport.KafkaEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionStreamingIntegrationTest {

    private static final String TOPIC = "fraud.decisions.v1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final RedpandaContainer redpanda = new RedpandaContainer("redpandadata/redpanda:v24.2.7");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionOperations transactionOperations;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        transactions.deleteAll();
        consumer = KafkaEvents.consumer(redpanda.getBootstrapServers(), TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void card_testing_burst_publishes_a_block_event_keyed_by_account() throws Exception {
        // WHY: the headline downstream guarantee — the 4th rapid charge for one account is BLOCKED and
        // that decision reaches the topic, keyed by accountId so consumers see per-account order.
        TransactionResponse last = null;
        for (int i = 1; i <= 4; i++) {
            last = post("idem_burst_" + i, request("acct_burst", "device_" + i, "1.00"));
        }
        assertThat(last.decision()).isEqualTo(Decision.BLOCK);

        List<ConsumerRecord<String, String>> records =
                KafkaEvents.drainByKey(consumer, "acct_burst", 4, Duration.ofSeconds(15));

        assertThat(records).hasSize(4);
        ConsumerRecord<String, String> fourth = records.get(3);
        assertThat(fourth.key()).isEqualTo("acct_burst"); // ordering key = accountId
        FraudDecisionEvent event = parse(fourth);
        assertThat(event.decision()).isEqualTo(Decision.BLOCK);
        assertThat(event.accountId()).isEqualTo("acct_burst");
        assertThat(event.factors()).anyMatch(factor -> factor.code() == ReasonCode.VELOCITY_1M);
    }

    @Test
    void mundane_purchase_publishes_an_approve_event() throws Exception {
        // WHY: a normal domestic purchase must publish an APPROVE with no manufactured risk factors.
        post("idem_mundane", request("acct_mundane", "device_known", "50.00"));

        List<ConsumerRecord<String, String>> records =
                KafkaEvents.drainByKey(consumer, "acct_mundane", 1, Duration.ofSeconds(15));

        assertThat(records).hasSize(1);
        FraudDecisionEvent event = parse(records.get(0));
        assertThat(event.decision()).isEqualTo(Decision.APPROVE);
        assertThat(event.score()).isEqualTo(0.0);
        assertThat(event.factors()).isEmpty();
    }

    @Test
    void idempotent_replay_publishes_exactly_one_event() throws Exception {
        // WHY: a retried payment is deduped to one stored decision; it must likewise emit exactly one
        // event, never a phantom second. Drain the full window so a duplicate would have surfaced.
        Map<String, Object> body = request("acct_idem", "device_idem", "50.00");
        post("idem_repeat", body);
        post("idem_repeat", body);

        List<ConsumerRecord<String, String>> records =
                KafkaEvents.drainByKey(consumer, "acct_idem", Integer.MAX_VALUE, Duration.ofSeconds(8));

        assertThat(records).hasSize(1);
    }

    @Test
    void a_rolled_back_transaction_publishes_no_event() {
        // WHY (CRITICAL, guards the AFTER_COMMIT decision): AFTER_COMMIT exists precisely so a
        // rolled-back payment never emits a phantom decision. Register the in-transaction
        // FraudDecisionRecorded exactly as the service does, then force the surrounding transaction to
        // roll back; not a single message may reach the topic. Without this, a future refactor that
        // published pre-commit would still pass every happy-path test above.
        Transaction txn = new Transaction(
                "tx_rollback", "idem_rollback", "acct_rollback", "card_rollback",
                Money.of("50.00", "USD"), "Test Merchant", "retail", "203.0.113.7", "US", "US",
                "device_rollback", Instant.parse("2026-06-08T10:15:30Z"));
        FraudDecision decision = new FraudDecision(
                Decision.APPROVE, 0.0, List.of(), Instant.parse("2026-06-08T10:15:30Z"), false);

        assertThatThrownBy(() -> transactionOperations.executeWithoutResult(status -> {
            events.publishEvent(new FraudDecisionRecorded(txn, decision));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        List<ConsumerRecord<String, String>> records =
                KafkaEvents.drainByKey(consumer, "acct_rollback", Integer.MAX_VALUE, Duration.ofSeconds(5));

        assertThat(records).isEmpty();
    }

    private FraudDecisionEvent parse(ConsumerRecord<String, String> record) throws Exception {
        return objectMapper.readValue(record.value(), FraudDecisionEvent.class);
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
