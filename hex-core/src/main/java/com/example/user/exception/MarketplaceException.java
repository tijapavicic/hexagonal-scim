package com.example.user.exception;

/**
 * Base exception for marketplace domain errors.
 *
 * All marketplace-specific exceptions inherit from this.
 * Allows callers to catch all marketplace errors uniformly.
 *
 * Example:
 *   try {
 *       // marketplace operation
 *   } catch (MarketplaceException e) {
 *       // log and handle marketplace error
 *   }
 */
public abstract class MarketplaceException extends RuntimeException {
    public MarketplaceException(String message) {
        super(message);
    }

    public MarketplaceException(String message, Throwable cause) {
        super(message, cause);
    }
}

