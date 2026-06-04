package com.example.user.api.dto;

import com.example.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for creating a new user")
public record CreateUserRequest(
        @Schema(description = "Unique e-mail address for the user", example = "alice@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Human-readable display name", example = "Alice Johnson")
        @NotBlank String displayName,

        @Schema(description = "User role", example = "BUYER", allowableValues = {"BUYER", "SELLER", "ADMIN"})
        UserRole role
) {
}



