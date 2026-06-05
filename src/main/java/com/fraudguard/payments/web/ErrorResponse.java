package com.fraudguard.payments.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single error contract for the payments API: every handled 4xx returns this shape so clients
 * parse one structure regardless of which validation tripped.
 */
@Schema(description = "Standard error response returned for handled 4xx payment API failures.")
public record ErrorResponse(
        @Schema(description = "HTTP status code.", example = "400")
        int status,

        @Schema(description = "HTTP reason phrase.", example = "Bad Request")
        String error,

        @Schema(description = "Specific validation or money-format failure.", example = "currency must match [A-Z]{3}")
        String message) {
}
