package com.example.user.exception;

/**
 * Raised when a seller is not found.
 * Returns 404 to caller (not 403, for security).
 */
public class SellerNotFoundException extends MarketplaceException {
    public SellerNotFoundException(String message) {
        super(message);
    }

    public SellerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

