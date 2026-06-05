package com.fraudguard.payments.web;

/**
 * The single error contract for the payments API: every handled 4xx returns this shape so clients
 * parse one structure regardless of which validation tripped.
 */
public record ErrorResponse(int status, String error, String message) {
}
