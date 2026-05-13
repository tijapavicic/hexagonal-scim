package com.example.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String name
) {
}

