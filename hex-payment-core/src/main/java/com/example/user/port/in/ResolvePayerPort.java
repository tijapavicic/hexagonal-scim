package com.example.user.port.in;

import java.util.Optional;

/**
 * Resolves a payer's system user ID from their email address.
 *
 * <p>Used by the payment inbound adapter to auto-populate {@code userId} from the
 * authenticated JWT's {@code email} claim, so clients never need to send their own
 * user ID in the request body.
 *
 * <p>Implemented at the application composition root ({@code hex-application}) where
 * both the user repository and the payment domain are in scope — keeping the two
 * bounded contexts (user management and payments) decoupled from each other.
 */
public interface ResolvePayerPort {

    /**
     * Returns the system user ID for the given email, or empty if no user with
     * that email exists in the system.
     */
    Optional<Long> findUserIdByEmail(String email);
}

