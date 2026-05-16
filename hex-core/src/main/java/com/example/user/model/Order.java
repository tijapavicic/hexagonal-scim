package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order aggregate root for marketplace orders.
 *
 * Represents a single purchase from one buyer to one seller.
 * Each order contains:
 * - Order items (products, quantities, prices at order time)
 * - Financial breakdown (gross amount, commission, seller payout)
 * - Status tracking (PENDING_PAYMENT → PAID → SHIPPED → DELIVERED → COMPLETED)
 * - Audit trail (created_at, updated_at)
 *
 * SOLID Principles:
 * - SRP: Only handles order business logic, not persistence
 * - OCP: Status transitions are closed for modification (enum-based)
 * - LSP: Immutable value semantics - substitutable across layers
 * - ISP: Focused interface - order operations only
 * - DIP: Domain model has no Spring/persistence dependencies
 *
 * Key Invariant: Status transitions must follow valid state machine
 *   PENDING_PAYMENT → PAID → SHIPPED → DELIVERED → COMPLETED
 *   CANCELLED can happen from any state
 *
 * Commission is calculated AFTER payment and never changes:
 *   commission = grossAmount * 0.05 (5% platform fee)
 *   sellerPayout = grossAmount * 0.95 (95% to seller)
 */
public record Order(
    Long id,                          // Unique order ID (assigned by database)
    Long buyerId,                     // Buyer user ID
    Long sellerId,                    // Seller user ID
    List<OrderItem> items,            // Line items in this order
    OrderStatus status,               // Cur rent order status
    BigDecimal grossAmount,           // Total before commission
    BigDecimal commissionAmount,      // 5% to platform
    BigDecimal sellerPayout,          // 95% to seller
    Long paymentId,                   // Payment reference
    LocalDateTime createdAt,          // Creation timestamp
    LocalDateTime updatedAt           // Last change timestamp
) {

    /**
     * Compact constructor - validates inputs on every construction.
     */
    public Order {
        if (buyerId != null && buyerId <= 0) {
            throw new IllegalArgumentException("buyerId must be positive");
        }
        if (sellerId != null && sellerId <= 0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
        if (status != null && items != null && (grossAmount != null && grossAmount.signum() <= 0)) {
            throw new IllegalArgumentException("grossAmount must be positive");
        }
    }

    /**
     * Factory method to create a new order.
     *
     * Order starts in PENDING_PAYMENT status with zero commission/payout
     * (commissioned after payment completes).
     *
     * @param buyerId buyer user ID
     * @param sellerId seller user ID
     * @param items order items (must not be empty)
     * @return new Order in PENDING_PAYMENT status
     * @throws IllegalArgumentException if inputs invalid
     */
    public static Order create(Long buyerId, Long sellerId, List<OrderItem> items) {
        // Validate inputs
        if (buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("buyerId must be positive");
        }
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        // Calculate gross amount
        BigDecimal grossAmount = items.stream()
            .map(OrderItem::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (grossAmount.signum() <= 0) {
            throw new IllegalArgumentException("grossAmount must be greater than 0");
        }

        LocalDateTime now = LocalDateTime.now();

        return new Order(
            null, // id (assigned by database)
            buyerId,
            sellerId,
            items,
            OrderStatus.PENDING_PAYMENT,
            grossAmount,
            BigDecimal.ZERO,    // commission not calculated yet
            BigDecimal.ZERO,    // payout not calculated yet
            null,               // paymentId not set yet
            now,
            now
        );
    }

    /**
     * Mark order as paid and record commission split.
     *
     * Validates state transition PENDING_PAYMENT → PAID
     * Locks in commission and seller payout amounts at pay time.
     *
     * @param paymentId payment transaction ID
     * @param commission commission amount (typically 5% of gross)
     * @param payout seller payout (typically 95% of gross)
     * @return new Order in PAID status
     * @throws IllegalStateException if not in PENDING_PAYMENT status
     */
    public Order markAsPaid(Long paymentId, BigDecimal commission, BigDecimal payout) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                "Can only mark PENDING_PAYMENT orders as paid, current: " + this.status
            );
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.PAID, this.grossAmount, commission, payout,
            paymentId, this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Mark order as shipped.
     */
    public Order markAsShipped() {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("Can only ship PAID orders, current: " + this.status);
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.SHIPPED, this.grossAmount, this.commissionAmount, this.sellerPayout,
            this.paymentId, this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Mark order as delivered.
     */
    public Order markAsDelivered() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Can only deliver SHIPPED orders, current: " + this.status);
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.DELIVERED, this.grossAmount, this.commissionAmount, this.sellerPayout,
            this.paymentId, this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Mark order as completed.
     */
    public Order markAsCompleted() {
        if (this.status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Can only complete DELIVERED orders, current: " + this.status);
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.COMPLETED, this.grossAmount, this.commissionAmount, this.sellerPayout,
            this.paymentId, this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Cancel order.
     */
    public Order cancel() {
        if (this.status == OrderStatus.COMPLETED || this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel " + this.status + " order");
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.CANCELLED, this.grossAmount, this.commissionAmount, this.sellerPayout,
            this.paymentId, this.createdAt, LocalDateTime.now()
        );
    }
}

