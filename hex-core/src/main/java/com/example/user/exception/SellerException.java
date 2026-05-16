package com.example.user.exception;

/**
 * Raised when seller operation fails.
 *
 * Examples:
 * - User already a seller
 * - User not allowed to become seller
 * - Seller account disabled
 */
public class SellerException extends MarketplaceException {
    public SellerException(String message) {
        super(message);
    }

    public SellerException(String message, Throwable cause) {
        super(message, cause);
    }
}

