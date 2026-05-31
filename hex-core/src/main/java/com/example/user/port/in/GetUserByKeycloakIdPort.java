package com.example.user.port.in;

import com.example.user.model.User;

/**
 * Port for retrieving a user by their Keycloak ID (external OAuth2 identity).
 *
 * <p>Use case: When a user authenticates via OAuth2/Keycloak, the JWT contains
 * the Keycloak user UUID. This port maps that external identity to the internal
 * User domain model.
 */
public interface GetUserByKeycloakIdPort {
    /**
     * Retrieve user by Keycloak UUID.
     *
     * @param keycloakId Keycloak user UUID from OAuth2 JWT
     * @return User domain model
     * @throws com.example.user.core.UserNotFoundException if no user with this keycloak_id exists
     */
    User getByKeycloakId(String keycloakId);
}

