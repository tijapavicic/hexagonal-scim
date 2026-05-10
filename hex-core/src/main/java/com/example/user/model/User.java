package com.example.user.model;

import java.util.Objects;

/**
 * Core domain model representing a user.
 *
 * <p>Invariants enforced by the compact constructor:
 * <ul>
 *   <li>{@code email} must be non-null and non-blank</li>
 *   <li>{@code displayName} must be non-null and non-blank</li>
 *   <li>{@code id} may be {@code null} for users that have not been persisted yet</li>
 * </ul>
 *
 * <p>Violation of any invariant throws {@link NullPointerException} (for {@code null})
 * or {@link IllegalArgumentException} (for blank), ensuring the domain model is
 * always in a valid state regardless of which adapter constructs it.
 */
public record User(Long id, String email, String displayName) {

    /** Compact constructor — enforces domain invariants on every construction site. */
    public User {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (email.isBlank())       throw new IllegalArgumentException("email must not be blank");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
    }
}

