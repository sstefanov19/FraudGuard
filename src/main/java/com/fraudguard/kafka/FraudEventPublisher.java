package com.fraudguard.kafka;

/** Seam for publishing decision events — lets tests swap the Kafka implementation. */
public interface FraudEventPublisher {

    void publish(FraudDecisionEvent event);
}
