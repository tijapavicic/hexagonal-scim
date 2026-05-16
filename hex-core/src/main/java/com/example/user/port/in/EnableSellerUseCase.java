package com.example.user.port.in;

import com.example.user.model.SellerProfile;
import com.example.user.model.SellerRegistration;

/**
 * Use case for enabling seller capabilities on a user account.
 *
 * Allows a buyer user to self-register as a seller.
 * Creates seller profile and initializes earnings tracking.
 * Triggers transactional workflow:
 *   1. Validate user not already a seller
 *   2. Enable seller on User entity
 *   3. Initialize SellerEarnings with zero balance
 *   4. Log seller registration event
 *
 * SOLID: ISP - Single responsibility port
 *        focused on seller registration only
 */
public interface EnableSellerUseCase {

    /**
     * Register authenticated user as seller.
     *
     * @param userId authenticated user ID
     * @param registration seller registration (name + bio)
     * @return created SellerProfile
     * @throws IllegalArgumentException if registration invalid
     * @throws com.example.user.exception.UserNotFoundException if user doesn't exist
     * @throws com.example.user.exception.SellerException if already a seller
     */
    SellerProfile execute(Long userId, SellerRegistration registration);
}

