package com.example.user.port.in;

import com.example.user.model.Product;

import java.math.BigDecimal;

public interface CreateProductPort {
    Product create(String name, String description, BigDecimal price, String currency, int stockQuantity);
}

