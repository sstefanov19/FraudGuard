package com.fraudguard.kafka;

import java.time.Instant;
import java.util.List;

import com.fraudguard.fraud.Decision;
import com.fraudguard.fraud.FraudDecision;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaFraudEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void a_synchronous_send_failure_is_swallowed_so_the_committed_response_stands() {
        // WHY: by the time AFTER_COMMIT fires the decision is already committed and the HTTP response
        // already returned. A broker hiccup that makes send() throw synchronously must never propagate
        // back into the committing thread and turn a successful authorization into a 500.
        KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(FraudDecisionEvent.class)))
                .thenThrow(new RuntimeException("broker down"));
        KafkaFraudEventPublisher publisher =
                new KafkaFraudEventPublisher(kafkaTemplate, "fraud.decisions.v1");

        Transaction txn = Transactions.usd("50.00");
        FraudDecision decision = new FraudDecision(
                Decision.APPROVE, 0.0, List.of(), Instant.parse("2026-06-08T10:15:30Z"), false);

        assertThatCode(() -> publisher.onDecisionRecorded(new FraudDecisionRecorded(txn, decision)))
                .doesNotThrowAnyException();
    }
}
