package com.example.user.api.dto;

import com.example.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a new user")
public record CreateUserRequest(
        @Schema(description = "Unique e-mail address for the user", example = "alice@example.com")
        @NotBlank
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @Schema(description = "Human-readable display name", example = "Alice Johnson")
        @NotBlank
        @Size(max = 100, message = "displayName must not exceed 100 characters")
        @Pattern(regexp = "^[^\\r\\n\\t\\x00-\\x1F\\x7F]*$",
                message = "displayName must not contain control characters")
        String displayName,

        @Schema(description = "User role", example = "BUYER", allowableValues = {"BUYER", "SELLER", "ADMIN"})
        UserRole role
) {
}
