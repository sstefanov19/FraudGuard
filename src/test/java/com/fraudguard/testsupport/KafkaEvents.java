package com.fraudguard.testsupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test-only Kafka consumer helpers. Values are read as raw JSON strings (then parsed with the app's
 * ObjectMapper) so the assertions exercise the real serialized wire form, not a Java round-trip.
 */
public final class KafkaEvents {

    private KafkaEvents() {
    }

    /** A fresh consumer in its own group reading the topic from the beginning. */
    public static KafkaConsumer<String, String> consumer(String bootstrapServers, String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /**
     * Poll until {@code expected} records carrying {@code key} are collected or {@code timeout}
     * elapses, preserving per-partition (and thus per-key) order. Pass {@link Integer#MAX_VALUE} as
     * {@code expected} to drain the whole window — the way to prove "exactly one / none".
     */
    public static List<ConsumerRecord<String, String>> drainByKey(
            KafkaConsumer<String, String> consumer, String key, int expected, Duration timeout) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && out.size() < expected) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (key.equals(record.key())) {
                    out.add(record);
                }
            }
        }
        return out;
    }
}
