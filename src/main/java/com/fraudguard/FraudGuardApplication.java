package com.fraudguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FraudGuard: a real-time, explainable fraud-detection service for a payments platform.
 *
 * <p>Build-order step 1 (this commit) ships the domain model and a synchronous,
 * rules-based fraud scorer with full unit-test coverage. No web layer, persistence,
 * Kafka, or Redis yet — the fraud decision is proven in isolation first.
 */
@SpringBootApplication
public class FraudGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudGuardApplication.class, args);
    }
}
