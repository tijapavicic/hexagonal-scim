package com.example.user.model;

import java.util.List;

/**
 * Immutable model for paginated user results.
 */
public record PagedUsers(
        List<User> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
}

