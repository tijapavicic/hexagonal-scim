package com.example.user.api.payment.dto;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String path,
        String timestamp
) {
    public ErrorResponse(String code, String message, String path) {
        this(code, message, path, Instant.now().toString());
    }
}

