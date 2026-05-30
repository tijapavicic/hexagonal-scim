package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Core domain model representing a user and seller in the marketplace.
 *
 * A User can be:
 * - A buyer only (isSeller = false, seller fields null)
 * - A seller (isSeller = true, seller fields populated)
 * - Both buyer and seller simultaneously
 *
 * <p>Invariants enforced by the compact constructor:
 * <ul>
 *   <li>{@code email} must be non-null and non-blank</li>
 *   <li>{@code displayName} must be non-null and non-blank (user's account display name)</li>
 *   <li>{@code id} may be {@code null} for users that have not been persisted yet</li>
 *   <li>If {@code isSeller} is true, seller fields must be present</li>
 *   <li>If {@code isSeller} is false, seller fields must be null</li>
 * </ul>
 *
 * <p>Violation of any invariant throws {@link NullPointerException} (for {@code null})
 * or {@link IllegalArgumentException} (for blank), ensuring the domain model is
 * always in a valid state regardless of which adapter constructs it.
 *
 * SOLID Principles:
 * - SRP: User handles both buyer and seller personas in one model
 * - OCP: Seller fields are optional, new seller features don't break existing code
 * - LSP: User is substitutable in both buyer and seller contexts
 * - ISP: Methods are focused (isSeller(), enableSeller(), etc)
 * - DIP: No Spring/infrastructure dependencies
 */
public record User(
    Long id,                      // Unique user ID
    String email,                 // User email (unique)
    String displayName,           // User's account display name
    Boolean isSeller,             // Whether user is registered as seller
    String sellerDisplayName,     // Seller's shop name (unique if not null)
    String sellerBio,             // Seller's shop bio/description
    BigDecimal sellerRating,      // Average seller rating (0-5 stars)
    Integer sellerReviewCount,    // Number of seller reviews
    LocalDateTime sellerVerifiedAt,  // When seller account was verified
    LocalDateTime sellerJoinedAt     // When user became a seller
) {

    /** Compact constructor — enforces domain invariants on every construction site. */
    public User {
        // Basic user validation
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (email.isBlank())       throw new IllegalArgumentException("email must not be blank");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");

        // Seller invariants
        if (isSeller == null) {
            isSeller = false;
        }

        if (isSeller) {
            // If seller, seller fields must be populated
            Objects.requireNonNull(sellerDisplayName, "sellerDisplayName required for sellers");
            if (sellerDisplayName.isBlank()) {
                throw new IllegalArgumentException("sellerDisplayName must not be blank");
            }
            if (sellerRating == null) {
                sellerRating = BigDecimal.ZERO;
            }
            if (sellerReviewCount == null) {
                sellerReviewCount = 0;
            }
        } else {
            // If not seller, seller fields must be null
            if (sellerDisplayName != null || sellerBio != null ||
                sellerRating != null || sellerReviewCount != null ||
                sellerVerifiedAt != null || sellerJoinedAt != null) {
                throw new IllegalArgumentException("non-sellers must have null seller fields");
            }
        }
    }

    /**
     * Factory to create a regular buyer user.
     *
     * @param id user ID
     * @param email user email
     * @param displayName display name
     * @return new User in buyer mode (not a seller)
     */
    public static User createBuyer(Long id, String email, String displayName) {
        return new User(id, email, displayName, false, null, null, null, null, null, null);
    }

    /**
     * Factory to create a user and immediately register as seller.
     *
     * @param id user ID
     * @param email user email
     * @param displayName user's account display name
     * @param sellerDisplayName seller's shop name
     * @param sellerBio seller's shop bio
     * @return new User in both buyer and seller mode
     */
    public static User createSeller(
        Long id, String email, String displayName,
        String sellerDisplayName, String sellerBio
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new User(
            id, email, displayName,
            true,                    // isSeller = true
            sellerDisplayName,
            sellerBio,
            BigDecimal.ZERO,        // Initial rating 0
            0,                       // Initial review count 0
            null,                    // Not yet verified
            now                      // Joined now
        );
    }

    /**
     * Enable seller capabilities on this user.
     *
     * Transitions user from buyer-only to seller+buyer.
     *
     * @param sellerDisplayName seller's shop name
     * @param sellerBio seller's shop bio
     * @return new User with seller capabilities enabled
     * @throws IllegalStateException if user is already a seller
     */
    public User enableSeller(String sellerDisplayName, String sellerBio) {
        if (this.isSeller) {
            throw new IllegalStateException("User is already a seller");
        }

        if (sellerDisplayName == null || sellerDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerDisplayName required");
        }

        LocalDateTime now = LocalDateTime.now();

        return new User(
            this.id,
            this.email,
            this.displayName,
            true,                    // Enable seller
            sellerDisplayName,
            sellerBio,
            BigDecimal.ZERO,        // Initial rating
            0,                       // Initial review count
            null,                    // Not yet verified by admin
            now                      // Joined at
        );
    }

    /**
     * Update seller profile information.
     *
     * Only updates seller fields if user is a seller.
     *
     * @param newSellerDisplayName new seller display name
     * @param newSellerBio new seller bio
     * @return new User with updated seller info
     * @throws IllegalStateException if user is not a seller
     */
    public User updateSellerProfile(String newSellerDisplayName, String newSellerBio) {
        if (!this.isSeller) {
            throw new IllegalStateException("User is not a seller");
        }

        if (newSellerDisplayName != null && newSellerDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerDisplayName must not be blank");
        }

        String displayName = newSellerDisplayName != null ? newSellerDisplayName : this.sellerDisplayName;
        String bio = newSellerBio; // Can be null/empty

        return new User(
            this.id,
            this.email,
            this.displayName,
            true,
            displayName,
            bio,
            this.sellerRating,
            this.sellerReviewCount,
            this.sellerVerifiedAt,
            this.sellerJoinedAt
        );
    }

    /**
     * Verify seller account (admin operation).
     *
     * Once verified, seller can be displayed publicly.
     *
     * @return new User with seller verified
     * @throws IllegalStateException if user is not a seller
     */
    public User verifySeller() {
        if (!this.isSeller) {
            throw new IllegalStateException("User is not a seller");
        }

        LocalDateTime now = LocalDateTime.now();

        return new User(
            this.id,
            this.email,
            this.displayName,
            true,
            this.sellerDisplayName,
            this.sellerBio,
            this.sellerRating,
            this.sellerReviewCount,
            now,                     // Set verified timestamp
            this.sellerJoinedAt
        );
    }

    /**
     * Update seller rating based on reviews.
     *
     * @param newRating new average rating (0-5)
     * @param newReviewCount new total review count
     * @return new User with updated rating
     * @throws IllegalStateException if user is not a seller
     * @throws IllegalArgumentException if rating out of bounds
     */
    public User updateSellerRating(BigDecimal newRating, Integer newReviewCount) {
        if (!this.isSeller) {
            throw new IllegalStateException("User is not a seller");
        }

        if (newRating == null) {
            throw new IllegalArgumentException("rating required");
        }

        if (newRating.compareTo(BigDecimal.ZERO) < 0 || newRating.compareTo(new BigDecimal("5")) > 0) {
            throw new IllegalArgumentException("rating must be between 0 and 5");
        }

        if (newReviewCount == null || newReviewCount < 0) {
            throw new IllegalArgumentException("reviewCount must be non-negative");
        }

        return new User(
            this.id,
            this.email,
            this.displayName,
            true,
            this.sellerDisplayName,
            this.sellerBio,
            newRating,
            newReviewCount,
            this.sellerVerifiedAt,
            this.sellerJoinedAt
        );
    }

    /**
     * Convenience helper for boolean seller checks.
     *
     * @return true when seller capability is enabled
     */
    public boolean sellerEnabled() {
        return Boolean.TRUE.equals(this.isSeller);
    }

    /**
     * Check if user is verified as seller.
     *
     * Verified sellers are displayed publicly.
     *
     * @return true if seller is verified
     */
    public boolean isSellerVerified() {
        return sellerEnabled() && this.sellerVerifiedAt != null;
    }
}
