package com.example.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;

/**
 * Request payload for a partial user update (HTTP PATCH / SCIM PATCH).
 * At least one field must be present; absent fields retain their current value.
 */
@Schema(description = "Partial user update payload — at least one field must be provided")
public record PatchUserRequest(

        @Schema(description = "New e-mail address (optional — omit to keep existing)", example = "newemail@example.com")
        @Email(message = "email must be a valid address")
        String email,

        @Schema(description = "New display name (optional — omit to keep existing)", example = "Alice")
        String displayName
) {
    /**
     * Bean Validation constraint: at least one of email or displayName must be supplied.
     * Evaluated by {@code @Valid} on the controller method parameter.
     */
    @AssertTrue(message = "at least one field (email or displayName) must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return email != null || displayName != null;
    }
}

