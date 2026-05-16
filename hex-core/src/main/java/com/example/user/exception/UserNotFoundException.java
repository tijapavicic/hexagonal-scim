package com.example.user.exception;

/**
 * Raised when a user is not found.
 * Returns 404 to caller.
 */
public class UserNotFoundException extends MarketplaceException {
    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

