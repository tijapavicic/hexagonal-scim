package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Financial transaction record for marketplace orders.
 *
 * Created when an order payment completes successfully.
 * Records the complete financial breakdown:
 * - gross_amount: total buyer payment
 * - commission_amount: 5% to platform
 * - seller_payout: 95% to seller
 *
 * Essential for:
 * - Seller earnings tracking
 * - Commission accounting
 * - Financial reconciliation
 * - Audit trail
 *
 * One transaction per successful order.
 *
 * SOLID: SRP - records financial split only
 *        immutable snapshot of payment facts
 */
public record Transaction(
    Long id,                       // Unique transaction ID
    Long orderId,                  // Reference to order
    Long buyerId,                  // Buyer user ID
    Long sellerId,                 // Seller user ID
    Long productId,                // Product purchased
    Integer quantity,              // Quantity purchased
    BigDecimal grossAmount,        // Gross transaction amount (buyer pays)
    BigDecimal commissionAmount,   // Commission deducted (5% to platform)
    BigDecimal sellerPayout,       // Amount credited to seller (95% of gross)
    Long paymentId,                // Payment transaction ID
    String paymentStatus,          // Payment status
    String transactionReference,   // Unique transaction reference
    String status,                 // Transaction status
    LocalDateTime createdAt,       // Created timestamp
    LocalDateTime updatedAt        // Updated timestamp
) {

    /**
     * Compact constructor - validates inputs.
     */
    public Transaction {
        if (orderId != null && orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (buyerId != null && buyerId <= 0) {
            throw new IllegalArgumentException("buyerId must be positive");
        }
        if (sellerId != null && sellerId <=0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
    }

    /**
     * Factory method to create a completed transaction.
     */
    public static Transaction complete(
        Long orderId, Long buyerId, Long sellerId, Long productId, Integer quantity,
        BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal sellerPayout,
        Long paymentId, String transactionReference
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Transaction(
            null, orderId, buyerId, sellerId, productId, quantity,
            grossAmount, commissionAmount, sellerPayout,
            paymentId, "COMPLETED", transactionReference,
            "COMPLETED", now, now
        );
    }

    /**
     * Factory method to create a pending transaction.
     */
    public static Transaction pending(
        Long orderId, Long buyerId, Long sellerId, Long productId, Integer quantity,
        BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal sellerPayout,
        Long paymentId, String transactionReference
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new Transaction(
            null, orderId, buyerId, sellerId, productId, quantity,
            grossAmount, commissionAmount, sellerPayout,
            paymentId, null, transactionReference,
            "PENDING", now, now
        );
    }
}

