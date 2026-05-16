package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aggregated financial summary for a seller.
 *
 * Tracks three key metrics:
 * - total_earned: sum of all seller payouts from transactions
 * - total_paid_out: sum of all completed payout requests
 * - pending_payout: available withdrawal balance = total_earned - total_paid_out
 *
 * One record per seller (primary key = seller_id).
 * Updated each time:
 * - Order is paid (increment total_earned by seller's share)
 * - Payout is completed (increment total_paid_out)
 *
 * Essential for seller self-service to view available balance
 * and request withdrawals.
 *
 * SOLID: SRP - only aggregates financial metrics for a seller
 *        - immutable domain model
 *        - validation of invariant: pending_payout = earned - paid_out
 */
public record SellerEarnings(
    Long sellerId,                // Seller user ID (unique identifier)
    BigDecimal totalEarned,       // Total earned from all transactions
    BigDecimal totalPaidOut,      // Total paid out via payout requests
    BigDecimal pendingPayout,     // Available balance for withdrawal
    LocalDateTime lastPayoutAt,   // Timestamp of last successful payout
    LocalDateTime createdAt,      // Created timestamp
    LocalDateTime updatedAt       // Updated timestamp
) {

    /**
     * Factory method to initialize seller earnings.
     *
     * Called when a user first becomes a seller.
     * Starts with zero balances.
     */
    public static SellerEarnings initialize(Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerEarnings(sellerId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, now, now);
    }

    /**
     * Record seller earnings from a completed transaction.
     */
    public SellerEarnings recordEarnings(BigDecimal transactionPayout) {
        if (transactionPayout == null || transactionPayout.signum() <= 0) {
            throw new IllegalArgumentException("transactionPayout must be positive");
        }
        BigDecimal newTotal = this.totalEarned.add(transactionPayout);
        BigDecimal newPending = newTotal.subtract(this.totalPaidOut);
        LocalDateTime now = LocalDateTime.now();
        return new SellerEarnings(this.sellerId, newTotal, this.totalPaidOut, newPending, this.lastPayoutAt, this.createdAt, now);
    }

    /**
     * Record a completed payout.
     */
    public SellerEarnings recordPayout(BigDecimal payoutAmount) {
        if (payoutAmount == null || payoutAmount.signum() <= 0) {
            throw new IllegalArgumentException("payoutAmount must be positive");
        }
        if (payoutAmount.compareTo(this.pendingPayout) > 0) {
            throw new IllegalStateException("Cannot payout " + payoutAmount + ": only " + this.pendingPayout + " available");
        }
        BigDecimal newPaidOut = this.totalPaidOut.add(payoutAmount);
        BigDecimal newPending = this.totalEarned.subtract(newPaidOut);
        LocalDateTime now = LocalDateTime.now();
        return new SellerEarnings(this.sellerId, this.totalEarned, newPaidOut, newPending, now, this.createdAt, now);
    }

    /**
     * Check if seller has sufficient balance for withdrawal.
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        return amount.compareTo(this.pendingPayout) <= 0;
    }
}

