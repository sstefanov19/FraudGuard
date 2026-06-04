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
 * A known-bad identifier is a hard stop: full severity, full weight, so a single blocklist hit
 * drives the score straight to BLOCK regardless of anything else.
 */
class BlocklistRuleTest {

    private final BlocklistRule rule = new BlocklistRule();
    private final Transaction txn = Transactions.usd("50.00");

    @Test
    void a_blocklisted_card_fires_at_maximum_contribution() {
        // WHY: if we already know this card is bad, no other signal should be able to soften it.
        FeatureSnapshot blockedCard = new FeatureSnapshotBuilder().cardOnBlocklist(true).build();

        RiskFactor factor = rule.evaluate(txn, blockedCard).orElseThrow();

        assertThat(factor.code()).isEqualTo(ReasonCode.BLOCKLIST_HIT);
        assertThat(factor.contribution()).isEqualTo(1.0);
        assertThat(factor.description()).contains("card");
    }

    @Test
    void a_blocklisted_ip_or_device_also_fires() {
        assertThat(rule.evaluate(txn, new FeatureSnapshotBuilder().ipOnBlocklist(true).build())).isPresent();
        assertThat(rule.evaluate(txn, new FeatureSnapshotBuilder().deviceOnBlocklist(true).build())).isPresent();
    }

    @Test
    void stays_silent_when_nothing_is_blocklisted() {
        assertThat(rule.evaluate(txn, new FeatureSnapshotBuilder().build())).isEmpty();
    }
}
