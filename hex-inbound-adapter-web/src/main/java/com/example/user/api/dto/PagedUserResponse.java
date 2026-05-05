package com.example.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Paginated response for user listings.
 */
@Schema(description = "Paginated list of user resources")
public record PagedUserResponse(
        @Schema(description = "Users on the current page") List<UserResponse> content,
        @Schema(description = "Zero-indexed current page number", example = "0") int pageNumber,
        @Schema(description = "Maximum items per page", example = "10") int pageSize,
        @Schema(description = "Total number of users across all pages", example = "25") long totalElements,
        @Schema(description = "Total number of pages", example = "3") int totalPages,
        @Schema(description = "Whether a next page exists", example = "true") boolean hasNext,
        @Schema(description = "Whether a previous page exists", example = "false") boolean hasPrevious
) {
}



