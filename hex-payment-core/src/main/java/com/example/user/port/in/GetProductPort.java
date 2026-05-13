package com.example.user.port.in;

import com.example.user.model.Product;

public interface GetProductPort {
    Product getById(Long id);
}

