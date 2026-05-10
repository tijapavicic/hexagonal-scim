package com.example.user.model;

import java.util.List;

/**
 * Immutable model for paginated user results.
 *
 * <p>The {@code content} list is defensively copied in the compact constructor —
 * callers cannot mutate the stored list after construction.
 */
public record PagedUsers(
        List<User> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
    /** Compact constructor — guarantees {@code content} is an unmodifiable snapshot. */
    public PagedUsers {
        content = (content == null) ? List.of() : List.copyOf(content);
    }
}

