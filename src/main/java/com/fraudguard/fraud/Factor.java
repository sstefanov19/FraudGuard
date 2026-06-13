package com.fraudguard.fraud;

/**
 * One risk factor as it appears on the wire. Deliberately slimmer than {@link RiskFactor}: it carries
 * only {@code contribution} (the rank/display number), not the {@code severity}/{@code weight} tuning
 * knobs the engine uses internally — those must not be frozen into a contract consumers depend on.
 */
public record Factor(ReasonCode code, String description, double contribution) {

    public static Factor from(RiskFactor factor) {
        return new Factor(factor.code(), factor.description(), factor.contribution());
    }
}
