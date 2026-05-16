package com.example.user.port.out;

import com.example.user.model.SellerEarnings;

/**
 * Output port for seller-specific persistence operations.
 *
 * Handles:
 * - SellerEarnings tracking and updates
 * - Seller verification
 * - Seller rating updates
 *
 * SOLID: DIP - use cases depend on port, not concrete implementation
 *        ISP - focused on seller domain concerns
 */
public interface SellerRepositoryPort {

    /**
     * Save seller earnings record.
     *
     * Called when user becomes a seller (initialize) or
     * after transaction completes (update).
     *
     * @param earnings seller earnings to persist
     * @return persisted earnings (with any DB-generated fields)
     */
    SellerEarnings saveEarnings(SellerEarnings earnings);

    /**
     * Find seller earnings by seller ID.
     *
     * @param sellerId seller user ID
     * @return seller earnings if found
     */
    java.util.Optional<SellerEarnings> findEarningsBySellerId(Long sellerId);

    /**
     * Update seller earnings (record new transaction payout).
     *
     * Used when order is paid and seller payout is recorded.
     *
     * @param sellerId seller ID
     * @param payoutAmount seller's cut from transaction
     */
    void recordSellerEarnings(Long sellerId, java.math.BigDecimal payoutAmount);
}

