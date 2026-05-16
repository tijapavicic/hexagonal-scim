package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seller payout request record.
 *
 * When a seller requests to withdraw pending earnings.
 * Tracks lifecycle of the withdrawal:
 * - REQUESTED: seller submitted request
 * - PROCESSING: platform is processing (optional state)
 * - COMPLETED: funds transferred to seller
 * - FAILED: transfer failed, manual review needed
 * - CANCELLED: seller or platform cancelled
 *
 * One record per withdrawal request.
 *
 * Immutable domain model.
 */
public record SellerPayout(
    Long id,                        // Unique payout ID
    Long sellerId,                  // Seller requesting payout
    BigDecimal amount,              // Amount requested for withdrawal
    String status,                  // Payout status
    String payoutMethod,            // Withdrawal method
    LocalDateTime requestedAt,      // When request was submitted
    LocalDateTime processingStartedAt, // When processing started
    LocalDateTime completedAt,      // When payout completed
    String transactionReference,    // Reference from payment processor
    String failureReason,           // Reason if payout failed
    String notes,                   // Admin notes
    LocalDateTime createdAt,        // Created timestamp
    LocalDateTime updatedAt         // Updated timestamp
) {

    /**
     * Factory method to create a new payout request.
     */
    public static SellerPayout request(Long sellerId, BigDecimal amount) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerPayout(null, sellerId, amount, "REQUESTED", null, now, null, null, null, null, null, now, now);
    }

    /**
     * Mark payout as processing.
     */
    public SellerPayout startProcessing() {
        if (!"REQUESTED".equals(this.status)) {
            throw new IllegalStateException("Can only start processing REQUESTED payouts, current: " + this.status);
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerPayout(this.id, this.sellerId, this.amount, "PROCESSING", this.payoutMethod, this.requestedAt, now, null, null, null, null, this.createdAt, now);
    }

    /**
     * Mark payout as successfully completed.
     */
    public SellerPayout complete(String transactionReference) {
        if (!"PROCESSING".equals(this.status)) {
            throw new IllegalStateException("Can only complete PROCESSING payouts, current: " + this.status);
        }
        if (transactionReference == null || transactionReference.trim().isEmpty()) {
            throw new IllegalArgumentException("transactionReference required");
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerPayout(this.id, this.sellerId, this.amount, "COMPLETED", this.payoutMethod, this.requestedAt, this.processingStartedAt, now, transactionReference, null, null, this.createdAt, now);
    }

    /**
     * Mark payout as failed.
     */
    public SellerPayout fail(String reason) {
        if (!"PROCESSING".equals(this.status)) {
            throw new IllegalStateException("Can only fail PROCESSING payouts, current: " + this.status);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("reason required");
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerPayout(this.id, this.sellerId, this.amount, "FAILED", this.payoutMethod, this.requestedAt, this.processingStartedAt, now, null, reason, null, this.createdAt, now);
    }

    /**
     * Cancel payout request.
     */
    public SellerPayout cancel() {
        if ("COMPLETED".equals(this.status) || "FAILED".equals(this.status) || "CANCELLED".equals(this.status)) {
            throw new IllegalStateException("Cannot cancel " + this.status + " payout");
        }
        LocalDateTime now = LocalDateTime.now();
        return new SellerPayout(this.id, this.sellerId, this.amount, "CANCELLED", this.payoutMethod, this.requestedAt, this.processingStartedAt, now, null, null, null, this.createdAt, now);
    }
}

