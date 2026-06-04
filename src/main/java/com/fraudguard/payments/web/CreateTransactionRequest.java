package com.fraudguard.payments.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotBlank String accountId,
        @NotBlank String cardFingerprint,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank String merchant,
        @NotBlank String merchantCategory,
        @NotBlank String ipAddress,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String geoCountry,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String billingCountry,
        @NotBlank String deviceId) {
}
