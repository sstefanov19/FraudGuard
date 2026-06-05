package com.fraudguard.payments.web;

import jakarta.validation.ConstraintViolationException;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Translates payment API errors into a single {@link ErrorResponse} contract so malformed-but-plausible
 * input returns a 4xx with a clear reason instead of a leaked 500 stacktrace, and clients parse one
 * error shape across every validation path.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidMoneyException.class)
    ResponseEntity<ErrorResponse> handleInvalidMoney(InvalidMoneyException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .findFirst()
                .orElse("Invalid input");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleHeaderAndParamValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("Invalid input");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // With @Validated on the controller, constrained @RequestHeader/@RequestParam violations arrive
    // as ConstraintViolationException (AOP method validation) rather than HandlerMethodValidationException.
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> "%s %s".formatted(v.getPropertyPath(), v.getMessage()))
                .findFirst()
                .orElse("Invalid input");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Omitting a required header raises MissingRequestHeaderException before @NotBlank runs; without
    // this, the common "no Idempotency-Key" case would return Spring's default body, not ErrorResponse.
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "missing required header " + ex.getHeaderName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "malformed request body");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
