package com.example.user.exception;

/**
 * Raised when order operation fails.
 *
 * Examples:
 * - Order not found
 * - Invalid status transition
 * - Order cannot be modified in current state
 */
public class OrderException extends MarketplaceException {
    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }
}

