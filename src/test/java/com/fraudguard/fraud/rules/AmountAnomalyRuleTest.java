package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FeatureSnapshotBuilder;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A charge far above an account's normal spend is a stolen-card / bust-out signal — but only when
 * there's enough history to know what "normal" is.
 */
class AmountAnomalyRuleTest {

    private final AmountAnomalyRule rule = new AmountAnomalyRule(5.0, 5, 0.40);

    @Test
    void fires_when_amount_is_far_above_the_trailing_average() {
        // WHY: an account that averages $40 suddenly spending $1,000 is the classic stolen-card
        // pattern — 25x the norm should score high.
        Transaction bigCharge = Transactions.usd("1000.00");
        FeatureSnapshot established = new FeatureSnapshotBuilder()
                .priorTransactionCount(20)
                .trailingAverageAmount(Money.of("40.00", "USD"))
                .build();

        RiskFactor factor = rule.evaluate(bigCharge, established).orElseThrow();

        assertThat(factor.code()).isEqualTo(ReasonCode.AMOUNT_ANOMALY);
        assertThat(factor.severity()).isEqualTo(1.0); // 25x avg is well past the 2x-multiple cap
    }

    @Test
    void stays_silent_for_a_normal_sized_purchase() {
        // WHY: spending in line with history is not anomalous; firing here would be a false positive.
        Transaction normalCharge = Transactions.usd("55.00");
        FeatureSnapshot established = new FeatureSnapshotBuilder()
                .priorTransactionCount(20)
                .trailingAverageAmount(Money.of("40.00", "USD"))
                .build();

        assertThat(rule.evaluate(normalCharge, established)).isEmpty();
    }

    @Test
    void stays_silent_without_enough_history_to_judge() {
        // WHY: with no baseline, "anomaly" is meaningless. A new account's first big purchase is
        // handled by other signals (new device), not by a trailing average that doesn't exist.
        Transaction bigCharge = Transactions.usd("1000.00");
        FeatureSnapshot brandNew = new FeatureSnapshotBuilder()
                .priorTransactionCount(1)
                .trailingAverageAmount(Money.of("40.00", "USD"))
                .build();

        assertThat(rule.evaluate(bigCharge, brandNew)).isEmpty();
    }
}
