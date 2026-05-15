package com.example.user.model;

import java.util.List;

/**
 * Represents a paginated list of payments.
 */
public record PagedPayments(
        List<Payment> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
}

