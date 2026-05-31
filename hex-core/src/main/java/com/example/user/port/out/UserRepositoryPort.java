package com.example.user.port.out;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);

    Optional<User> findById(Long id);

    /** Looks up a user by their exact (case-insensitive) email address. */
    Optional<User> findByEmail(String email);

    /** Looks up a user by their Keycloak UUID (external identity). */
    Optional<User> findByKeycloakId(String keycloakId);

    boolean existsByEmail(String email);

    /** Returns a window of users, sorted by id ascending. */
    PagedUsers findAll(int pageNumber, int pageSize);

    /** Returns every user in the database, sorted by id ascending. */
    List<User> findAll();

    /**
     * Persists field changes to an existing user.
     * The caller is responsible for verifying existence and e-mail uniqueness before calling this method.
     */
    User update(User user);

    /** Removes the user with the given id from the store. */
    void deleteById(Long id);

    /**
     * Find seller by seller display name (shop name).
     *
     * Used for looking up seller profiles publicly.
     * Seller display names are unique.
     *
     * @param sellerDisplayName seller's shop name
     * @return seller user if found
     */
    Optional<User> findBySellerDisplayName(String sellerDisplayName);
}

