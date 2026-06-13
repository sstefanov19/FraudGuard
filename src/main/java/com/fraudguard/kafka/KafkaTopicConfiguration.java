package com.fraudguard.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the decisions topic so {@code KafkaAdmin} auto-creates it in dev/test. Replication 1 is a
 * dev choice; production should pre-provision the topic with real replication (noted in PROGRESS).
 */
@Configuration
public class KafkaTopicConfiguration {

    @Bean
    NewTopic fraudDecisions(
            @Value("${fraud.kafka.topic:fraud.decisions.v1}") String topic,
            @Value("${fraud.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
