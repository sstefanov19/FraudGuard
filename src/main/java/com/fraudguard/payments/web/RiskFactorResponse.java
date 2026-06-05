package com.fraudguard.payments.web;

import com.fraudguard.fraud.ReasonCode;
import com.fraudguard.fraud.RiskFactor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Machine-readable and human-readable explanation for one fraud signal.")
public record RiskFactorResponse(
        @Schema(description = "Stable reason code for this signal.")
        ReasonCode code,

        @Schema(description = "Human-readable explanation with signal-specific context.",
                example = "4 transactions in the last minute")
        String description,

        @Schema(description = "Normalized rule severity from 0.0 to 1.0.", example = "1.0")
        double severity,

        @Schema(description = "Rule weight applied to the severity.", example = "0.45")
        double weight,

        @Schema(description = "Score contribution after severity and weight are combined.", example = "0.45")
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
