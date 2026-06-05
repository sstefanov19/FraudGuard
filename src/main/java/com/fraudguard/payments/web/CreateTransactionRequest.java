package com.fraudguard.payments.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// @Size / @Digits bounds mirror the V1__transactions column limits so oversized input returns a
// 400 via ApiExceptionHandler instead of a DataIntegrityViolationException surfacing as a 500.
@Schema(description = "Payment authorization details to persist and score for fraud risk.")
public record CreateTransactionRequest(
        @Schema(description = "Stable customer account identifier.", example = "acct_9f3a", maxLength = 128)
        @NotBlank @Size(max = 128) String accountId,

        @Schema(description = "Tokenized or hashed payment card fingerprint.", example = "card_fp_2f8d", maxLength = 255)
        @NotBlank @Size(max = 255) String cardFingerprint,

        @Schema(description = "Authorization amount.", example = "50.00", minimum = "0.01")
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,

        @Schema(description = "ISO 4217 currency code.", example = "USD", pattern = "[A-Z]{3}")
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,

        @Schema(description = "Merchant display name.", example = "Test Merchant", maxLength = 255)
        @NotBlank @Size(max = 255) String merchant,

        @Schema(description = "Merchant category or vertical.", example = "retail", maxLength = 128)
        @NotBlank @Size(max = 128) String merchantCategory,

        @Schema(description = "Originating IP address captured by the payment platform.", example = "203.0.113.7", maxLength = 64)
        @NotBlank @Size(max = 64) String ipAddress,

        @Schema(description = "ISO 3166-1 alpha-2 country inferred from the request.", example = "US", pattern = "[A-Z]{2}")
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String geoCountry,

        @Schema(description = "ISO 3166-1 alpha-2 billing country.", example = "US", pattern = "[A-Z]{2}")
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String billingCountry,

        @Schema(description = "Device fingerprint observed for this authorization.", example = "device_17", maxLength = 255)
        @NotBlank @Size(max = 255) String deviceId) {
}
