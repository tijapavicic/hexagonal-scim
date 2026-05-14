package com.example.user.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorization interceptor that enforces role-based access control on all {@code /api/**} endpoints.
 *
 * <p>Rules:
 * <ul>
 *   <li>Unauthenticated requests are passed through — Spring Security's filter chain
 *       is responsible for returning {@code 401 Unauthorized}.</li>
 *   <li>Authenticated users must carry at least {@code ROLE_USER} or {@code ROLE_ADMIN}
 *       to access any endpoint.</li>
 *   <li>Mutating operations ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE})
 *       additionally require {@code ROLE_ADMIN}.</li>
 * </ul>
 *
 * <p>Denials are logged at {@code WARN} level with principal, client IP, method, and path
 * to support security audit trails. The message returned to the caller is intentionally
 * generic — detail stays in the log, not in the response body.
 *
 * <p>An {@link AccessDeniedException} thrown here is caught by
 * {@link com.example.user.api.ApiExceptionHandlerAdapter} (via {@code @RestControllerAdvice})
 * and serialised as a JSON {@code 403} response — ensuring a consistent error envelope.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationInterceptor.class);

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // No authentication present → let Spring Security's entry point return 401
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        // Single pass over authorities — avoids double-streaming for write-method checks
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isUser  = roles.contains("ROLE_USER");

        if (!isAdmin && !isUser) {
            log.warn("Access denied — no recognised role. principal={} roles={} ip={} method={} path={}",
                    auth.getName(), roles, request.getRemoteAddr(),
                    request.getMethod(), request.getRequestURI());
            throw new AccessDeniedException("Insufficient permissions");
        }

        if (WRITE_METHODS.contains(request.getMethod()) && !isAdmin) {
            log.warn("Access denied — write operation requires ROLE_ADMIN. principal={} ip={} method={} path={}",
                    auth.getName(), request.getRemoteAddr(),
                    request.getMethod(), request.getRequestURI());
            throw new AccessDeniedException("Insufficient permissions");
        }

        return true;
    }
}

