package com.example.user.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Core domain model representing a user in the marketplace.
 *
 * A User has one of three roles:
 * - BUYER (default): Can browse and purchase products
 * - SELLER: Can sell products + all buyer capabilities (requires seller profile)
 * - ADMIN: Platform administrator + all seller/buyer capabilities
 *
 * <p>Invariants enforced by the compact constructor:
 * <ul>
 *   <li>{@code email} must be non-null and non-blank</li>
 *   <li>{@code displayName} must be non-null and non-blank (user's account display name)</li>
 *   <li>{@code role} must be non-null (defaults to BUYER)</li>
 *   <li>{@code id} may be {@code null} for users that have not been persisted yet</li>
 *   <li>If {@code role} is SELLER or ADMIN, seller fields should be present</li>
 *   <li>If {@code role} is BUYER, seller fields must be null</li>
 * </ul>
 *
 * <p>Violation of any invariant throws {@link NullPointerException} (for {@code null})
 * or {@link IllegalArgumentException} (for blank), ensuring the domain model is
 * always in a valid state regardless of which adapter constructs it.
 *
 * SOLID Principles:
 * - SRP: User handles identity and role-based capabilities
 * - OCP: New roles can be added without breaking existing code
 * - LSP: User is substitutable in any role context
 * - ISP: Methods are focused (role checks, profile updates, etc)
 * - DIP: No Spring/infrastructure dependencies
 */
public record User(
    Long id,                      // Unique user ID (internal auto-increment)
    String keycloakId,            // Keycloak user UUID (external identity)
    String email,                 // User email (unique)
    String displayName,           // User's account display name
    UserRole role,                // User role (BUYER, SELLER, ADMIN)
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

        // Role defaults to BUYER if null
        if (role == null) {
            role = UserRole.BUYER;
        }

        // Seller/Admin invariants
        if (role.canSell()) {
            // If seller or admin, seller fields should be populated
            if (sellerDisplayName != null && sellerDisplayName.isBlank()) {
                throw new IllegalArgumentException("sellerDisplayName must not be blank");
            }
            // Initialize defaults for new sellers
            if (sellerRating == null) {
                sellerRating = BigDecimal.ZERO;
            }
            if (sellerReviewCount == null) {
                sellerReviewCount = 0;
            }
        } else {
            // If buyer, seller fields must be null
            if (sellerDisplayName != null || sellerBio != null ||
                sellerRating != null || sellerReviewCount != null ||
                sellerVerifiedAt != null || sellerJoinedAt != null) {
                throw new IllegalArgumentException("buyers must have null seller fields");
            }
        }
    }

    /**
     * Factory to create a regular buyer user.
     *
     * @param id user ID
     * @param keycloakId Keycloak user UUID (can be null)
     * @param email user email
     * @param displayName display name
     * @return new User with BUYER role
     */
    public static User createBuyer(Long id, String keycloakId, String email, String displayName) {
        return new User(id, keycloakId, email, displayName, UserRole.BUYER, null, null, null, null, null, null);
    }

    /**
     * Factory to create a user with SELLER role.
     *
     * @param id user ID
     * @param keycloakId Keycloak user UUID (can be null)
     * @param email user email
     * @param displayName user's account display name
     * @param sellerDisplayName seller's shop name
     * @param sellerBio seller's shop bio
     * @return new User with SELLER role
     */
    public static User createSeller(
        Long id, String keycloakId, String email, String displayName,
        String sellerDisplayName, String sellerBio
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new User(
            id, keycloakId, email, displayName,
            UserRole.SELLER,         // Role = SELLER
            sellerDisplayName,
            sellerBio,
            BigDecimal.ZERO,        // Initial rating 0
            0,                       // Initial review count 0
            null,                    // Not yet verified
            now                      // Joined now
        );
    }

    /**
     * Factory to create an admin user.
     *
     * @param id user ID
     * @param keycloakId Keycloak user UUID (can be null)
     * @param email user email
     * @param displayName user's account display name
     * @return new User with ADMIN role
     */
    public static User createAdmin(Long id, String keycloakId, String email, String displayName) {
        return new User(id, keycloakId, email, displayName, UserRole.ADMIN, null, null, null, null, null, null);
    }

    /**
     * Promote user to SELLER role.
     *
     * Transitions user from BUYER to SELLER.
     *
     * @param sellerDisplayName seller's shop name
     * @param sellerBio seller's shop bio
     * @return new User with SELLER role
     * @throws IllegalStateException if user is already a seller or admin
     */
    public User enableSeller(String sellerDisplayName, String sellerBio) {
        if (this.role.canSell()) {
            throw new IllegalStateException("User is already a seller or admin");
        }

        if (sellerDisplayName == null || sellerDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerDisplayName required");
        }

        LocalDateTime now = LocalDateTime.now();

        return new User(
            this.id,
            this.keycloakId,
            this.email,
            this.displayName,
            UserRole.SELLER,         // Promote to SELLER
            sellerDisplayName,
            sellerBio,
            BigDecimal.ZERO,        // Initial rating
            0,                       // Initial review count
            null,                    // Not yet verified by admin
            now                      // Joined at
        );
    }

    /**
     * Update user role.
     *
     * Admin operation to change user role.
     *
     * @param newRole new role to assign
     * @return new User with updated role
     * @throws IllegalArgumentException if newRole is null
     */
    public User updateRole(UserRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("role required");
        }

        // When downgrading from seller/admin to buyer, clear seller fields
        if (!newRole.canSell() && this.role.canSell()) {
            return new User(
                this.id,
                this.keycloakId,
                this.email,
                this.displayName,
                newRole,
                null,  // Clear seller fields
                null,
                null,
                null,
                null,
                null
            );
        }

        // Otherwise just update role, keep existing data
        return new User(
            this.id,
            this.keycloakId,
            this.email,
            this.displayName,
            newRole,
            this.sellerDisplayName,
            this.sellerBio,
            this.sellerRating,
            this.sellerReviewCount,
            this.sellerVerifiedAt,
            this.sellerJoinedAt
        );
    }

    /**
     * Update seller profile information.
     *
     * Only updates seller fields if user has seller or admin role.
     *
     * @param newSellerDisplayName new seller display name
     * @param newSellerBio new seller bio
     * @return new User with updated seller info
     * @throws IllegalStateException if user cannot sell
     */
    public User updateSellerProfile(String newSellerDisplayName, String newSellerBio) {
        if (!this.role.canSell()) {
            throw new IllegalStateException("User does not have seller capabilities");
        }

        if (newSellerDisplayName != null && newSellerDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerDisplayName must not be blank");
        }

        String displayName = newSellerDisplayName != null ? newSellerDisplayName : this.sellerDisplayName;
        String bio = newSellerBio; // Can be null/empty

        return new User(
            this.id,
            this.keycloakId,
            this.email,
            this.displayName,
            this.role,
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
     * @throws IllegalStateException if user cannot sell
     */
    public User verifySeller() {
        if (!this.role.canSell()) {
            throw new IllegalStateException("User does not have seller capabilities");
        }

        LocalDateTime now = LocalDateTime.now();

        return new User(
            this.id,
            this.keycloakId,
            this.email,
            this.displayName,
            this.role,
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
     * @throws IllegalStateException if user cannot sell
     * @throws IllegalArgumentException if rating out of bounds
     */
    public User updateSellerRating(BigDecimal newRating, Integer newReviewCount) {
        if (!this.role.canSell()) {
            throw new IllegalStateException("User does not have seller capabilities");
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
            this.keycloakId,
            this.email,
            this.displayName,
            this.role,
            this.sellerDisplayName,
            this.sellerBio,
            newRating,
            newReviewCount,
            this.sellerVerifiedAt,
            this.sellerJoinedAt
        );
    }

    /**
     * Convenience helper for role-based seller checks.
     *
     * @return true when user has SELLER or ADMIN role
     */
    public boolean canSell() {
        return this.role != null && this.role.canSell();
    }

    /**
     * Check if user is admin.
     *
     * @return true when user has ADMIN role
     */
    public boolean isAdmin() {
        return this.role != null && this.role.isAdmin();
    }

    /**
     * Check if user is verified as seller.
     *
     * Verified sellers are displayed publicly.
     *
     * @return true if seller is verified
     */
    public boolean isSellerVerified() {
        return canSell() && this.sellerVerifiedAt != null;
    }
}
