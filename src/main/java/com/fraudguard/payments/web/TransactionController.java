package com.fraudguard.payments.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    @Operation(summary = "Score a payment authorization", description = "Persists the authorization, computes fraud features, stores the decision, and returns an explanation.")
    @ApiResponse(
            responseCode = "201",
            description = "Transaction scored",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request body or idempotency key",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TransactionResponse> create(
            @Parameter(description = "Client-generated key used to dedupe payment retries.", required = true, schema = @Schema(minLength = 1, maxLength = 255), example = "auth_20260605_000001") @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(idempotencyKey, request));
    }
}
