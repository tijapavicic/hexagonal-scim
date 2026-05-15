package com.example.user.port.in;

import com.example.user.model.PagedPayments;
import com.example.user.model.Payment;

import java.util.List;

public interface GetAllPaymentsPort {
    /**
     * Retrieves all payments (unpaginated).
     * @deprecated Use {@link #getAll(int, int, boolean)} for paginated results
     */
    @Deprecated(forRemoval = false)
    List<Payment> getAll();

    /**
     * Retrieves payments with pagination support.
     *
     * @param page      zero-indexed page number
     * @param size      items per page
     * @param pageable  if false, returns all payments regardless of page/size
     * @return paginated payments wrapped in PagedPayments
     */
    PagedPayments getAll(int page, int size, boolean pageable);
}

