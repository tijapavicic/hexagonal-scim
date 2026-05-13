package com.example.user.model;

import java.util.Objects;

public record Account(Long id, Long userId, String name) {

    public Account {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}

