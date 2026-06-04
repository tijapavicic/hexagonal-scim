package com.example.user.api.dto;

import com.example.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for a full user replacement (HTTP PUT).
 * All fields are mandatory — the existing user is completely replaced.
 */
@Schema(description = "Full user replacement payload — all fields required")
public record UpdateUserRequest(

        @Schema(description = "New e-mail address (must be unique)", example = "alice@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @Schema(description = "New display name", example = "Alice Smith")
        @NotBlank(message = "displayName is required")
        String displayName,

        @Schema(description = "User role", example = "BUYER", allowableValues = {"BUYER", "SELLER", "ADMIN"})
        UserRole role
) {}

