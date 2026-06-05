package com.fraudguard.payments.web;

/**
 * Thrown when a request carries a monetary value {@link com.fraudguard.payments.domain.Money} cannot
 * accept: an unsupported currency code, or more fraction digits than the currency allows (e.g. 50.999
 * USD). Mapped to a 400 by {@link ApiExceptionHandler} instead of letting Money's unchecked exceptions
 * surface as a 500.
 */
class InvalidMoneyException extends RuntimeException {

    InvalidMoneyException(String message) {
        super(message);
    }
}
