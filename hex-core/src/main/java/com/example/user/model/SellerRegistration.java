package com.example.user.model;

/**
 * Seller registration input value object.
 *
 * Immutable input model for seller onboarding.
 * Validates seller profile information at construction time
 * (fail-fast validation).
 *
 * SOLID: SRP - only validates seller registration data
 *        - no persistence or business logic
 */
public record SellerRegistration(
    String displayName,    // Public seller shop name (required, 3-100 chars)
    String bio             // Seller shop bio (optional, max 500 chars)
) {

    /**
     * Compact constructor - validates on every construction.
     */
    public SellerRegistration {
        // Validate displayName
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName is required");
        }

        displayName = displayName.trim();

        if (displayName.length() < 3) {
            throw new IllegalArgumentException("displayName must be at least 3 characters");
        }

        if (displayName.length() > 100) {
            throw new IllegalArgumentException("displayName must be at most 100 characters");
        }

        // Validate bio
        if (bio != null) {
            if (bio.trim().isEmpty()) {
                // treat empty as null
                bio = null;
            } else if (bio.length() > 500) {
                throw new IllegalArgumentException("bio must be at most 500 characters");
            }
        }
    }

    /**
     * Factory to create seller registration with validation.
     *
     * @param displayName shop name (3-100 chars)
     * @param bio shop description (optional, max 500 chars)
     * @return new SellerRegistration if valid
     * @throws IllegalArgumentException if validation fails
     */
    public static SellerRegistration create(String displayName, String bio) {
        // Validation is done in compact constructor
        return new SellerRegistration(displayName, bio);
    }
}

