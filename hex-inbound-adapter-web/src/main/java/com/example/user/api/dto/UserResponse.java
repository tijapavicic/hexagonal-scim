package com.example.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User resource representation")
public record UserResponse(
        @Schema(description = "Auto-generated unique identifier", example = "1") Long id,
        @Schema(description = "User e-mail address", example = "alice@example.com") String email,
        @Schema(description = "Human-readable display name", example = "Alice Johnson") String displayName
) {
}



