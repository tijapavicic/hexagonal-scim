package com.example.user.api.dto;

import com.example.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a partial user update (HTTP PATCH / SCIM PATCH).
 * At least one field must be present; absent fields retain their current value.
 */
@Schema(description = "Partial user update payload — at least one field must be provided")
public record PatchUserRequest(

        @Schema(description = "New e-mail address (optional — omit to keep existing)", example = "newemail@example.com")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @Schema(description = "New display name (optional — omit to keep existing)", example = "Alice")
        @Size(max = 100, message = "displayName must not exceed 100 characters")
        @Pattern(regexp = "^[^\\r\\n\\t\\x00-\\x1F\\x7F]*$",
                message = "displayName must not contain control characters")
        String displayName,

        @Schema(description = "New role (optional — omit to keep existing)", example = "SELLER", allowableValues = {"BUYER", "SELLER", "ADMIN"})
        UserRole role
) {
    /**
     * Bean Validation constraint: at least one of email, displayName, or role must be supplied.
     * Evaluated by {@code @Valid} on the controller method parameter.
     */
    @AssertTrue(message = "at least one field (email, displayName, or role) must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return email != null || displayName != null || role != null;
    }
}
