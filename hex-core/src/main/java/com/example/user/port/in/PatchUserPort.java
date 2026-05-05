package com.example.user.port.in;

import com.example.user.model.User;

/**
 * Inbound port for partial user update (HTTP PATCH / SCIM PATCH).
 * Only non-null fields are applied; fields that are {@code null} are left unchanged.
 */
public interface PatchUserPort {
    /**
     * Applies a partial update to the user identified by {@code id}.
     * Any parameter that is {@code null} is ignored and the existing value is preserved.
     *
     * @param id          the user to update
     * @param email       new e-mail address, or {@code null} to keep existing
     * @param displayName new display name, or {@code null} to keep existing
     * @return the updated user
     * @throws com.example.user.core.UserNotFoundException    if no user exists with the given id
     * @throws com.example.user.core.DuplicateUserException   if the new e-mail is already taken by another user
     */
    User patch(Long id, String email, String displayName);
}

