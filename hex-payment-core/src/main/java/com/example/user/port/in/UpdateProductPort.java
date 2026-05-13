package com.example.user.port.in;

import com.example.user.model.Product;

import java.math.BigDecimal;

public interface UpdateProductPort {
    Product update(Long id, String name, String description, BigDecimal price, String currency, int stockQuantity);
}

