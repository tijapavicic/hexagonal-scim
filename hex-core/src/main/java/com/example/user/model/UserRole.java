package com.example.user.model;

/**
 * User role in the marketplace.
 *
 * <p>Roles follow a simple hierarchy:
 * <ul>
 *   <li><strong>BUYER</strong> — Default role, can purchase products</li>
 *   <li><strong>SELLER</strong> — Can sell products + all buyer capabilities</li>
 *   <li><strong>ADMIN</strong> — Platform administrator + all seller capabilities</li>
 * </ul>
 *
 * <p>SOLID Principles:
 * - SRP: Enum defines role identity only
 * - OCP: New roles can be added without breaking existing code
 * - LSP: Roles are substitutable in authorization checks
 * - ISP: Minimal interface (just enum values)
 * - DIP: No infrastructure dependencies
 */
public enum UserRole {
    /**
     * Default user role — can browse and purchase products.
     */
    BUYER,

    /**
     * Seller role — can list products for sale + all buyer capabilities.
     * Requires seller profile information (shop name, bio, etc).
     */
    SELLER,

    /**
     * Platform administrator — full access to all features.
     * Can manage users, verify sellers, handle disputes, etc.
     */
    ADMIN;

    /**
     * Check if this role has seller capabilities.
     *
     * @return true for SELLER and ADMIN
     */
    public boolean canSell() {
        return this == SELLER || this == ADMIN;
    }

    /**
     * Check if this role has admin capabilities.
     *
     * @return true for ADMIN only
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}

