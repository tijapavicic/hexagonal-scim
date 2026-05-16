package com.example.user.port.in;

import com.example.user.model.SellerProfile;

/**
 * Use case for updating authenticated seller's profile information.
 *
 * Allows sellers to update their shop name and bio.
 * Only the authenticated seller can update their own profile.
 *
 * SOLID: ISP - focused on profile update only
 */
public interface UpdateSellerProfileUseCase {

    /**
     * Update seller profile.
     *
     * @param authenticatedSellerId authenticated seller user ID
     * @param sellerDisplayName new seller display name
     * @param sellerBio new seller bio
     * @return updated SellerProfile
     * @throws com.example.user.exception.SellerNotFoundException if seller doesn't exist
     * @throws com.example.user.exception.UnauthorizedSellerOperationException if authenticated user is not the seller
     * @throws IllegalArgumentException if inputs invalid
     */
    SellerProfile execute(Long authenticatedSellerId, String sellerDisplayName, String sellerBio);
}

