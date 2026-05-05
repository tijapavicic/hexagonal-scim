package com.example.user.port.in;

import com.example.user.model.User;

/**
 * Inbound port for full user replacement (HTTP PUT).
 * Both fields are mandatory; the existing user is completely replaced.
 */
public interface UpdateUserPort {
    /**
     * Replaces every field of the user identified by {@code id}.
     *
     * @param id          the user to replace
     * @param email       new e-mail address — must be unique across all users
     * @param displayName new display name
     * @return the updated user
     * @throws com.example.user.core.UserNotFoundException    if no user exists with the given id
     * @throws com.example.user.core.DuplicateUserException   if the new e-mail is already taken
     */
    User update(Long id, String email, String displayName);
}

