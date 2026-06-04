package com.fraudguard.payments.web;

import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;

public record RiskFactorResponse(
        ReasonCode code,
        String description,
        double severity,
        double weight,
        double contribution) {

    public static RiskFactorResponse from(RiskFactor factor) {
        return new RiskFactorResponse(
                factor.code(),
                factor.description(),
                factor.severity(),
                factor.weight(),
                factor.contribution());
    }
}
