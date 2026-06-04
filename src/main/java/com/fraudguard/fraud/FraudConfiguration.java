package com.fraudguard.fraud;

import com.fraudguard.fraud.rules.AmountAnomalyRule;
import com.fraudguard.fraud.rules.BlocklistRule;
import com.fraudguard.fraud.rules.GeoMismatchRule;
import com.fraudguard.fraud.rules.NewDeviceHighValueRule;
import com.fraudguard.fraud.rules.Rule;
import com.fraudguard.fraud.rules.VelocityRule;
import com.fraudguard.payments.domain.Money;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default fraud engine: the rule set, their weights, the decision thresholds, and the
 * scoring service. Weights are the tunable knobs the Fraud Lab will eventually edit; they live
 * here as data, not scattered through the rule classes.
 *
 * <p>Calibration notes:
 * <ul>
 *   <li>The 1-minute velocity rule reaches full severity at one over threshold (weight 1.0), so
 *       the 4th charge in 60s scores 1.0 -> BLOCK on its own (the card-testing signature).</li>
 *   <li>Geo mismatch (0.34) + 1-hour velocity + new-device-high-value (0.10) stack into the
 *       REVIEW band, matching the worked example in the design doc.</li>
 * </ul>
 */
@Configuration
public class FraudConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DecisionThresholds decisionThresholds() {
        return DecisionThresholds.defaults();
    }

    @Bean
    public List<Rule> fraudRules() {
        return List.of(
                new VelocityRule(ReasonCode.VELOCITY_1M, 3, 1, 1.00, FeatureSnapshot::txnCountLastMinute, "minute"),
                new VelocityRule(ReasonCode.VELOCITY_1H, 10, 10, 0.40, FeatureSnapshot::txnCountLastHour, "hour"),
                new AmountAnomalyRule(5.0, 5, 0.40),
                new GeoMismatchRule(0.34),
                new NewDeviceHighValueRule(Money.of("1000.00", "USD"), 0.10),
                new BlocklistRule());
    }

    @Bean
    public FraudScorer fraudScorer(List<Rule> fraudRules, DecisionThresholds decisionThresholds, Clock clock) {
        return new RuleBasedScorer(fraudRules, decisionThresholds, clock);
    }

    @Bean
    public ScoringService scoringService(FraudScorer fraudScorer, Clock clock) {
        return new ScoringService(fraudScorer, clock);
    }
}
