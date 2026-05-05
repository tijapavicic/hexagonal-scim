package com.example.user.api.dto;

import java.util.List;

/**
 * Paginated response for user listings.
 */
public record PagedUserResponse(
        List<UserResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}

