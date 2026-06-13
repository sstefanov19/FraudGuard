package com.fraudguard.kafka;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.features.FeatureProvider;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionRepository;
import com.fraudguard.payments.web.TransactionResponse;
import com.fraudguard.testsupport.KafkaEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
import org.testcontainers.redpanda.RedpandaContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DegradedDecisionStreamingIntegrationTest {

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

    @BeforeEach
    void clearTransactions() {
        transactions.deleteAll();
    }

    /** Replaces the real provider with one whose query fails against the live DB, aborting the write
     *  transaction the way a production DB error would and forcing the degraded-REVIEW path. */
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
    void a_degraded_review_is_published_as_an_event() throws Exception {
        // WHY: the fail-to-REVIEW guarantee must be visible downstream too — a feature-store outage
        // emits a degraded REVIEW event (score null) so the dashboard shows the held payment, never a
        // silent gap. This is the second of the two publishing paths (the degraded fresh-txn commit).
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idem_degraded_stream");

        ResponseEntity<TransactionResponse> response = rest.postForEntity(
                "/transactions", new HttpEntity<>(request(), headers), TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().degraded()).isTrue();

        try (KafkaConsumer<String, String> consumer = KafkaEvents.consumer(redpanda.getBootstrapServers(), TOPIC)) {
            List<ConsumerRecord<String, String>> records =
                    KafkaEvents.drainByKey(consumer, "acct_degraded_stream", 1, Duration.ofSeconds(15));

            assertThat(records).hasSize(1);
            FraudDecisionEvent event = objectMapper.readValue(records.get(0).value(), FraudDecisionEvent.class);
            assertThat(event.degraded()).isTrue();
            assertThat(event.decision()).isEqualTo(Decision.REVIEW);
            assertThat(event.score()).isNull();
        }
    }

    private Map<String, Object> request() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", "acct_degraded_stream");
        body.put("cardFingerprint", "card_degraded");
        body.put("amount", new BigDecimal("50.00"));
        body.put("currency", "USD");
        body.put("merchant", "Test Merchant");
        body.put("merchantCategory", "retail");
        body.put("ipAddress", "203.0.113.7");
        body.put("geoCountry", "US");
        body.put("billingCountry", "US");
        body.put("deviceId", "device_degraded");
        return body;
    }
}
