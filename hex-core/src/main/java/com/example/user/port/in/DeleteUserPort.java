package com.example.user.port.in;

/**
 * Inbound port for user deletion (HTTP DELETE).
 */
public interface DeleteUserPort {
    /**
     * Permanently deletes the user identified by {@code id}.
     *
     * @param id the user to delete
     * @throws com.example.user.core.UserNotFoundException if no user exists with the given id
     */
    void deleteById(Long id);
}

