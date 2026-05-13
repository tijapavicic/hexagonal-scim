package com.example.user.port.in;

import com.example.user.model.Payment;

public interface GetPaymentPort {
    Payment getById(Long id);
}

