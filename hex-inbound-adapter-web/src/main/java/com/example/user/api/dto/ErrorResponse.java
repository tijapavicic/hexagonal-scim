package com.example.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response returned when a request cannot be processed")
public record ErrorResponse(
        @Schema(description = "Machine-readable error code", example = "USER_NOT_FOUND") String code,
        @Schema(description = "Human-readable error message", example = "User not found for id: 99") String message
) {
}



