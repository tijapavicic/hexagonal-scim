package com.example.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Uniform error envelope returned by {@link com.example.user.api.ApiExceptionHandlerAdapter}.
 *
 * <p>All four fields are always present in the JSON response. {@code path} and
 * {@code timestamp} are filled automatically by the exception handler; callers that
 * use the two-arg convenience constructor receive {@code null} for path and the current
 * instant for timestamp.
 */
@Schema(description = "Error response returned when a request cannot be processed")
public record ErrorResponse(
        @Schema(description = "Machine-readable error code", example = "USER_NOT_FOUND")
        String code,

        @Schema(description = "Human-readable error message", example = "User not found for id: 99")
        String message,

        @Schema(description = "Request path that triggered the error", example = "/api/v1/users/99")
        String path,

        @Schema(description = "UTC timestamp of the error (ISO-8601)", example = "2026-05-11T10:15:30.123Z")
        String timestamp
) {
    /** Convenience constructor for handlers that already supply path + timestamp separately. */
    public ErrorResponse(String code, String message) {
        this(code, message, null, Instant.now().toString());
    }
}
