package com.example.user.api.payment.dto;

import java.util.List;

/**
 * Paginated payment response matching the contract of paginated user responses.
 */
public record PagedPaymentResponse(
        List<PaymentResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}

