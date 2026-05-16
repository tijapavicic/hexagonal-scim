package com.example.user.exception;

/**
 * Raised when seller tries to access/modify another seller's data.
 *
 * Examples:
 * - Seller A trying to update Seller B's product
 * - Seller trying to cancel another seller's order
 *
 * Returns 404 NOT_FOUND (not 403) to prevent information disclosure.
 */
public class UnauthorizedSellerOperationException extends MarketplaceException {
    private final Long authenticatedSellerId;
    private final Long targetSellerId;
    private final String operation;

    public UnauthorizedSellerOperationException(
        String message,
        Long authenticatedSellerId,
        Long targetSellerId,
        String operation
    ) {
        super(message);
        this.authenticatedSellerId = authenticatedSellerId;
        this.targetSellerId = targetSellerId;
        this.operation = operation;
    }

    public Long getAuthenticatedSellerId() {
        return authenticatedSellerId;
    }

    public Long getTargetSellerId() {
        return targetSellerId;
    }

    public String getOperation() {
        return operation;
    }
}

