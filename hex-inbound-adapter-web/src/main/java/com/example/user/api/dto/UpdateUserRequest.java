package com.example.user.api.dto;

import com.example.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a full user replacement (HTTP PUT).
 * All fields are mandatory — the existing user is completely replaced.
 */
@Schema(description = "Full user replacement payload — all fields required")
public record UpdateUserRequest(

        @Schema(description = "New e-mail address (must be unique)", example = "alice@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @Schema(description = "New display name", example = "Alice Smith")
        @NotBlank(message = "displayName is required")
        @Size(max = 100, message = "displayName must not exceed 100 characters")
        @Pattern(regexp = "^[^\\r\\n\\t\\x00-\\x1F\\x7F]*$",
                message = "displayName must not contain control characters")
        String displayName,

        @Schema(description = "User role", example = "BUYER", allowableValues = {"BUYER", "SELLER", "ADMIN"})
        UserRole role
) {}
