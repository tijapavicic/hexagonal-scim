package com.example.user.api;

import com.example.user.api.dto.ErrorResponse;
import com.example.user.core.AccountNotFoundException;
import com.example.user.core.DuplicateUserException;
import com.example.user.core.DuplicateAccountException;
import com.example.user.core.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Central error handler for all REST controllers.
 *
 * <p>Every handler returns a uniform {@link ErrorResponse} with {@code code},
 * {@code message}, {@code path}, and {@code timestamp}. Spring Security exceptions
 * ({@link AccessDeniedException}, {@link AuthenticationException}) thrown from
 * {@link com.example.user.api.config.AuthorizationInterceptor} are caught here so that
 * the same JSON envelope is used — rather than the default Spring Security HTML/text body.
 */
@RestControllerAdvice
public class ApiExceptionHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandlerAdapter.class);

    // ─── Domain exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateUserException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(DuplicateUserException ex, HttpServletRequest req) {
        return error("USER_ALREADY_EXISTS", ex.getMessage(), req);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return error("USER_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest req) {
        return error("ACCOUNT_NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateAccount(DuplicateAccountException ex, HttpServletRequest req) {
        return error("ACCOUNT_ALREADY_EXISTS", ex.getMessage(), req);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed");
        return error("VALIDATION_ERROR", detail, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // Root cause may contain low-level parse details — don't expose to client
        log.debug("Unreadable request body on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return error("MALFORMED_REQUEST", "Request body is missing or cannot be parsed", req);
    }

    /**
     * Handles {@link MethodArgumentTypeMismatchException} thrown when path/query parameters
     * cannot be converted to the expected type (e.g., passing UUID string when Long is expected).
     * Returns clear error message indicating the expected type.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String paramName = ex.getName();
        String providedValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        
        log.warn("Type mismatch on {} {}: param='{}', provided='{}', expected={}",
                req.getMethod(), req.getRequestURI(), paramName, providedValue, expectedType);
        
        String message = String.format(
            "Invalid value '%s' for parameter '%s': expected %s",
            providedValue, paramName, expectedType
        );
        return error("INVALID_PARAMETER_TYPE", message, req);
    }

    // ─── Domain invariant violations ─────────────────────────────────────────

    /**
     * Handles {@link IllegalArgumentException} thrown when domain model invariants are violated
     * (e.g. blank email or displayName reaching the {@link com.example.user.model.User} compact
     * constructor). These are client errors — the payload supplied values the domain cannot accept.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return error("INVALID_REQUEST", ex.getMessage(), req);
    }

    // ─── Security exceptions ──────────────────────────────────────────────────

    /**
     * Handles {@link AccessDeniedException} thrown from
     * {@link com.example.user.api.config.AuthorizationInterceptor} or from
     * {@code @PreAuthorize} checks.
     *
     * <p><strong>Note:</strong> this handler fires because the interceptor runs
     * inside {@code DispatcherServlet} — after all security filters.  The exception
     * is therefore handled by Spring MVC's {@code HandlerExceptionResolver} first,
     * before Spring Security's {@code ExceptionTranslationFilter} can intercept it.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("403 Forbidden: {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return error("ACCESS_DENIED", ex.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        log.warn("401 Unauthorized: {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return error("UNAUTHORIZED", ex.getMessage(), req);
    }

    // ─── HTTP protocol errors ─────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return error("METHOD_NOT_ALLOWED", ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
        return error("NOT_FOUND",
                "No endpoint found for " + req.getMethod() + " " + req.getRequestURI(), req);
    }

    // ─── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error("INTERNAL_ERROR", "An unexpected error occurred", req);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ErrorResponse error(String code, String message, HttpServletRequest req) {
        return new ErrorResponse(code, message, req.getRequestURI(), Instant.now().toString());
    }
}
