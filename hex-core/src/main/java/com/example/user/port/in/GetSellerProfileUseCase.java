package com.example.user.port.in;

import com.example.user.model.SellerProfile;

/**
 * Use case for retrieving a seller's public profile.
 *
 * Used by:
 * - Buyers viewing seller information
 * - Frontend displaying seller details
 *
 * SOLID: ISP - focused on reading seller profile only
 */
public interface GetSellerProfileUseCase {

    /**
     * Get a seller's public profile.
     *
     * @param sellerId seller user ID
     * @return seller profile
     * @throws com.example.user.exception.SellerNotFoundException if seller doesn't exist
     */
    SellerProfile execute(Long sellerId);
}

