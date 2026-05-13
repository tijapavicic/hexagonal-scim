package com.example.user.api.dto;

public record AccountResponse(
        Long id,
        Long userId,
        String name
) {
}

