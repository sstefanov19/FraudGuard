package com.fraudguard.fraud.rules;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.fraud.FeatureSnapshotBuilder;
import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import com.fraudguard.payments.domain.Transaction;
import com.fraudguard.testsupport.Transactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A transaction from a country other than the account's home is a possible takeover / stolen-card
 * signal. Weighted to land in REVIEW, not BLOCK, because it's often just travel.
 */
class GeoMismatchRuleTest {

    private final GeoMismatchRule rule = new GeoMismatchRule(0.34);

    @Test
    void fires_when_transaction_country_differs_from_home() {
        // WHY: a US account suddenly transacting from DE warrants a look.
        Transaction abroad = Transactions.usd("120.00", "DE");
        FeatureSnapshot usAccount = new FeatureSnapshotBuilder().accountHomeCountry("US").build();

        RiskFactor factor = rule.evaluate(abroad, usAccount).orElseThrow();

        assertThat(factor.code()).isEqualTo(ReasonCode.GEO_COUNTRY_MISMATCH);
        assertThat(factor.contribution()).isEqualTo(0.34); // severity 1.0 x weight 0.34
        assertThat(factor.description()).contains("DE").contains("US");
    }

    @Test
    void stays_silent_for_a_domestic_transaction() {
        // WHY: same-country spend is the norm; firing would flag nearly everyone.
        Transaction domestic = Transactions.usd("120.00", "US");
        FeatureSnapshot usAccount = new FeatureSnapshotBuilder().accountHomeCountry("US").build();

        assertThat(rule.evaluate(domestic, usAccount)).isEmpty();
    }

    @Test
    void is_case_insensitive_about_country_codes() {
        // WHY: "us" and "US" are the same country; a casing quirk must not manufacture a mismatch.
        Transaction domestic = Transactions.usd("120.00", "us");
        FeatureSnapshot usAccount = new FeatureSnapshotBuilder().accountHomeCountry("US").build();

        assertThat(rule.evaluate(domestic, usAccount)).isEmpty();
    }
}
