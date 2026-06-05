package com.fraudguard.payments.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// @Size / @Digits bounds mirror the V1__transactions column limits so oversized input returns a
// 400 via ApiExceptionHandler instead of a DataIntegrityViolationException surfacing as a 500.
public record CreateTransactionRequest(
        @NotBlank @Size(max = 128) String accountId,
        @NotBlank @Size(max = 255) String cardFingerprint,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 255) String merchant,
        @NotBlank @Size(max = 128) String merchantCategory,
        @NotBlank @Size(max = 64) String ipAddress,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String geoCountry,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String billingCountry,
        @NotBlank @Size(max = 255) String deviceId) {
}
