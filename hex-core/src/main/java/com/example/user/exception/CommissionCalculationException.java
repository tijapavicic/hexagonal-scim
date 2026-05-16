package com.example.user.exception;

/**
 * Raised when commission calculation fails.
 *
 * Examples:
 * - Commission + payout doesn't equal gross amount
 * - Invalid rounding or precision issues
 */
public class CommissionCalculationException extends MarketplaceException {
    public CommissionCalculationException(String message) {
        super(message);
    }

    public CommissionCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}

