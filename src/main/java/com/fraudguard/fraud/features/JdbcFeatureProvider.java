package com.fraudguard.fraud.features;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FeatureSnapshotBuilder;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.payments.persistence.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdbcFeatureProvider implements FeatureProvider {

    private final TransactionRepository transactions;
    private final Duration minuteWindow;
    private final Duration hourWindow;
    private final int trailingWindowSize;

    public JdbcFeatureProvider(
            TransactionRepository transactions,
            @Value("${fraud.features.minute-window-seconds:60}") long minuteWindowSeconds,
            @Value("${fraud.features.hour-window-seconds:3600}") long hourWindowSeconds,
            @Value("${fraud.features.trailing-window-size:30}") int trailingWindowSize) {
        this.transactions = transactions;
        this.minuteWindow = Duration.ofSeconds(minuteWindowSeconds);
        this.hourWindow = Duration.ofSeconds(hourWindowSeconds);
        this.trailingWindowSize = trailingWindowSize;
    }

    @Override
    public FeatureSnapshot load(Transaction transaction) {
        int lastMinute = Math.toIntExact(transactions.countByDetailsAccountIdAndDetailsCreatedAtAfter(
                transaction.accountId(), transaction.createdAt().minus(minuteWindow)));
        int lastHour = Math.toIntExact(transactions.countByDetailsAccountIdAndDetailsCreatedAtAfter(
                transaction.accountId(), transaction.createdAt().minus(hourWindow)));
        int priorCount = Math.toIntExact(transactions.priorCount(
                transaction.accountId(), transaction.id(), transaction.amount().currency().getCurrencyCode()));
        boolean seenDevice = transactions.existsByDetailsAccountIdAndDetailsDeviceIdAndIdNot(
                transaction.accountId(), transaction.deviceId(), transaction.id());
        BigDecimal average = transactions.trailingAverage(
                transaction.accountId(),
                transaction.id(),
                transaction.amount().currency().getCurrencyCode(),
                trailingWindowSize);

        return new FeatureSnapshotBuilder()
                .txnCountLastMinute(lastMinute)
                .txnCountLastHour(lastHour)
                .priorTransactionCount(priorCount)
                .trailingAverageAmount(toMoney(average, transaction))
                .newDevice(!seenDevice)
                .accountHomeCountry(null)
                .cardOnBlocklist(false)
                .ipOnBlocklist(false)
                .deviceOnBlocklist(false)
                .build();
    }

    private Money toMoney(BigDecimal amount, Transaction transaction) {
        if (amount == null) {
            return null;
        }
        int scale = transaction.amount().currency().getDefaultFractionDigits();
        BigDecimal rounded = amount.setScale(scale, RoundingMode.HALF_UP);
        return Money.of(rounded, transaction.amount().currency().getCurrencyCode());
    }
}
