package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Money;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end scoring: rules fire, contributions sum, the score maps to a decision. Built from the
 * SAME rule set the app ships ({@link FraudConfiguration}) so the test can't pass while production
 * behaves differently.
 */
class RuleBasedScorerTest {

    private final FraudConfiguration config = new FraudConfiguration();
    private final FraudScorer scorer = new RuleBasedScorer(
            config.fraudRules(),
            config.decisionThresholds(),
            Clock.fixed(Instant.parse("2026-06-04T09:42:15Z"), ZoneOffset.UTC));

    @Test
    void card_testing_burst_blocks() {
        // WHY: this is the project's headline guarantee — the 4th $1 charge in 60 seconds is
        // card-testing and must be BLOCKED, on this signal alone.
        Transaction smallCharge = Transactions.usd("1.00");
        FeatureSnapshot fourInAMinute = new FeatureSnapshotBuilder().txnCountLastMinute(4).build();

        FraudDecision decision = scorer.score(smallCharge, fourInAMinute);

        assertThat(decision.decision()).isEqualTo(Decision.BLOCK);
        assertThat(decision.score()).isEqualTo(1.0);
        assertThat(decision.factors()).anyMatch(f -> f.code() == ReasonCode.VELOCITY_1M);
    }

    @Test
    void a_mundane_purchase_is_approved_with_no_risk_factors() {
        // WHY: an established account making a normal domestic purchase on a known device should
        // sail through — no factors, score 0, APPROVE. False positives churn real customers.
        Transaction normal = Transactions.usd("42.00");
        FeatureSnapshot established = new FeatureSnapshotBuilder()
                .priorTransactionCount(50)
                .trailingAverageAmount(Money.of("45.00", "USD"))
                .build();

        FraudDecision decision = scorer.score(normal, established);

        assertThat(decision.decision()).isEqualTo(Decision.APPROVE);
        assertThat(decision.score()).isEqualTo(0.0);
        assertThat(decision.factors()).isEmpty();
    }

    @Test
    void foreign_high_value_charge_on_a_new_device_is_reviewed_not_blocked() {
        // WHY: stacked soft signals (geo mismatch + new-device-high-value) should hold for a human,
        // not auto-block — this is the account-takeover-suspect path, and a wrong BLOCK is costly.
        Transaction abroadBigCharge = Transactions.usd("1500.00", "DE");
        FeatureSnapshot suspicious = new FeatureSnapshotBuilder()
                .accountHomeCountry("US")
                .newDevice(true)
                .build();

        FraudDecision decision = scorer.score(abroadBigCharge, suspicious);

        assertThat(decision.decision()).isEqualTo(Decision.REVIEW);
        assertThat(decision.factors()).anyMatch(f -> f.code() == ReasonCode.GEO_COUNTRY_MISMATCH);
        assertThat(decision.factors()).anyMatch(f -> f.code() == ReasonCode.NEW_DEVICE_HIGH_VALUE);
    }

    @Test
    void a_blocklisted_card_blocks_on_its_own() {
        // WHY: a known-bad card must block even on an otherwise unremarkable transaction.
        Transaction normal = Transactions.usd("42.00");
        FeatureSnapshot blocked = new FeatureSnapshotBuilder().cardOnBlocklist(true).build();

        assertThat(scorer.score(normal, blocked).decision()).isEqualTo(Decision.BLOCK);
    }
}
