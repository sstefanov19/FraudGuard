package com.fraudguard.fraud.features;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionEntity;
import com.fraudguard.payments.persistence.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class JdbcFeatureProviderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private FeatureProvider featureProvider;

    @BeforeEach
    void clearTransactions() {
        transactions.deleteAll();
    }

    @Test
    void velocity_windows_include_current_transaction_and_exclude_rows_outside_the_boundary() {
        // WHY: the card-testing guarantee depends on counting the just-persisted authorization,
        // while a row older than the rolling window must not inflate the burst.
        Instant now = Instant.parse("2026-06-04T10:00:00Z");
        save(tx("tx_old", "acct_velocity", "device_a", "1.00", now.minusSeconds(61)));
        save(tx("tx_recent", "acct_velocity", "device_b", "1.00", now.minusSeconds(59)));
        Transaction current = tx("tx_current", "acct_velocity", "device_c", "1.00", now);
        save(current);

        FeatureSnapshot features = featureProvider.load(current);

        assertThat(features.txnCountLastMinute()).isEqualTo(2);
        assertThat(features.txnCountLastHour()).isEqualTo(3);
    }

    @Test
    void trailing_average_and_prior_count_exclude_the_current_transaction() {
        // WHY: the current transaction is the thing being judged; including it in its own
        // baseline would dilute amount-anomaly detection.
        Instant now = Instant.parse("2026-06-04T10:00:00Z");
        save(tx("tx_1", "acct_average", "device_a", "10.00", now.minusSeconds(300)));
        save(tx("tx_2", "acct_average", "device_b", "30.00", now.minusSeconds(200)));
        Transaction current = tx("tx_current", "acct_average", "device_c", "999.00", now);
        save(current);

        FeatureSnapshot features = featureProvider.load(current);

        assertThat(features.priorTransactionCount()).isEqualTo(2);
        assertThat(features.trailingAverageAmount()).isEqualTo(Money.of("20.00", "USD"));
    }

    @Test
    void new_device_flips_when_the_account_has_prior_history_on_that_device() {
        // WHY: first-seen device is an account-takeover signal; it must be scoped to account and
        // must ignore the current row that was just flushed.
        Instant now = Instant.parse("2026-06-04T10:00:00Z");
        Transaction firstSeen = tx("tx_new_device", "acct_device", "device_new", "50.00", now);
        save(firstSeen);

        assertThat(featureProvider.load(firstSeen).newDevice()).isTrue();

        Transaction knownDevice = tx("tx_known_device", "acct_device", "device_new", "50.00", now.plusSeconds(10));
        save(knownDevice);

        assertThat(featureProvider.load(knownDevice).newDevice()).isFalse();
    }

    private void save(Transaction transaction) {
        transactions.saveAndFlush(TransactionEntity.fromDomain(transaction));
    }

    private Transaction tx(String id, String accountId, String deviceId, String amount, Instant createdAt) {
        return new Transaction(
                id,
                "idem_" + id,
                accountId,
                "card_" + accountId,
                Money.of(amount, "USD"),
                "Test Merchant",
                "retail",
                "203.0.113.7",
                "US",
                "US",
                deviceId,
                createdAt);
    }
}
