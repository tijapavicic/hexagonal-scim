package com.example.user.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collection;
import java.util.Set;

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
 * <p>An {@link AccessDeniedException} thrown here is caught by
 * {@link ApiExceptionHandlerAdapter} (via {@code @RestControllerAdvice}) and serialised
 * as a JSON {@code 403} response — ensuring a consistent error envelope.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // No authentication present → let Spring Security's entry point return 401
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        boolean hasRequiredRole = authorities.stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Access denied: ROLE_USER or ROLE_ADMIN required");
        }

        if (WRITE_METHODS.contains(request.getMethod())) {
            boolean hasAdminRole = authorities.stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdminRole) {
                throw new AccessDeniedException(
                        "Access denied: ROLE_ADMIN required for " + request.getMethod() + " operations");
            }
        }

        return true;
    }
}

