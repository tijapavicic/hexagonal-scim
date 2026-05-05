package com.example.user.port.in;

import com.example.user.model.PagedUsers;

/**
 * Use case port for retrieving a paginated list of users.
 */
public interface GetAllUsersPort {
    /**
     * Retrieve users, optionally paginated.
     *
     * @param pageNumber zero-indexed page number; ignored when {@code pageable} is {@code false}
     * @param pageSize   items per page (default 10, max 100); ignored when {@code pageable} is {@code false}
     * @param pageable   when {@code true} (default) returns a single page;
     *                   when {@code false} returns every user in the database in one response
     * @return paged user response
     */
    PagedUsers getAll(int pageNumber, int pageSize, boolean pageable);
}

