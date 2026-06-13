package com.fraudguard.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@link FraudDecisionEvent}s to Kafka after the scoring transaction commits.
 *
 * <p>The listener fires on {@code AFTER_COMMIT} only: a rolled-back payment never reaches here, so no
 * phantom decision is ever published. The send is asynchronous and its failure is logged, never
 * thrown — by the time this runs the decision is already committed and the HTTP response already
 * returned, so a broker hiccup must not surface as a 500. That leaves one accepted risk: if the
 * broker is down at publish time the audit event is dropped (logged ERROR); the transactional-outbox
 * TODO closes that gap.
 */
@Component
public class KafkaFraudEventPublisher implements FraudEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaFraudEventPublisher.class);

    private final KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate;
    private final String topic;

    public KafkaFraudEventPublisher(
            KafkaTemplate<String, FraudDecisionEvent> kafkaTemplate,
            @Value("${fraud.kafka.topic:fraud.decisions.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecisionRecorded(FraudDecisionRecorded recorded) {
        try {
            FraudDecisionEvent event = FraudDecisionEvent.of(
                    recorded.transaction(), recorded.decision(), UUID.randomUUID().toString());
            publish(event);
        } catch (RuntimeException e) {
            // A synchronous failure (e.g. serialization) must not propagate into the committing thread.
            log.error("Failed to publish fraud decision event for transaction {}",
                    recorded.transaction().id(), e);
        }
    }

    @Override
    public void publish(FraudDecisionEvent event) {
        kafkaTemplate.send(topic, event.accountId(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                // Accepted risk: decision already committed, broker send failed → this audit event is
                // dropped. Logged loud; the transactional-outbox TODO closes this gap.
                log.error("Dropped fraud decision event {} for account {} — broker send failed",
                        event.eventId(), event.accountId(), ex);
            } else {
                log.debug("Published fraud decision event {} to {}-{}@{}", event.eventId(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
