package com.example.user.port.in;

import com.example.user.model.Payment;

import java.util.List;

public interface GetAllPaymentsPort {
    List<Payment> getAll();
}

