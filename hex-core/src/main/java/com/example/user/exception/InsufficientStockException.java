package com.example.user.exception;

/**
 * Raised when product stock is insufficient for purchase.
 *
 * Returns 409 CONFLICT to caller.
 */
public class InsufficientStockException extends MarketplaceException {
    private final Long productId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientStockException(
        String message,
        Long productId,
        Integer requestedQuantity,
        Integer availableQuantity
    ) {
        super(message);
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
}

