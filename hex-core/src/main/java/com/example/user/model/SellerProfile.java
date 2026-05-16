package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seller profile value object.
 *
 * Public-facing seller information displayed to buyers.
 * Immutable snapshot of seller attributes.
 *
 * SOLID: SRP - represents seller profile data only
 *        - no business logic
 *        - can be constructed from database query results or User entity
 */
public record SellerProfile(
    Long sellerId,                // Seller's user ID
    String displayName,           // Public seller shop name (unique)
    String bio,                   // Seller shop bio/description
    BigDecimal rating,            // Average rating from buyer reviews (0-5 stars)
    Integer reviewCount,          // Total number of reviews from buyers
    LocalDateTime verifiedAt,     // When seller account was verified
    LocalDateTime joinedAt,       // When seller joined the platform
    Boolean isActive              // Whether seller account is active
) {

    /**
     * Factory to create seller profile.
     *
     * @param sellerId seller ID
     * @param displayName shop name
     * @param bio shop description
     * @param rating seller rating (0-5)
     * @param reviewCount number of reviews
     * @param verifiedAt verification timestamp
     * @param joinedAt joined timestamp
     * @return SellerProfile
     */
    public static SellerProfile create(
        Long sellerId, String displayName, String bio,
        BigDecimal rating, Integer reviewCount,
        LocalDateTime verifiedAt, LocalDateTime joinedAt
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId must be positive");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName required");
        }

        return new SellerProfile(
            sellerId, displayName, bio,
            rating != null ? rating : BigDecimal.ZERO,
            reviewCount != null ? reviewCount : 0,
            verifiedAt, joinedAt, true
        );
    }
}

