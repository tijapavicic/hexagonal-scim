package com.example.user.api;

import com.example.user.api.config.AuthorizationInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms that account endpoints ({@code /api/v1/users/{userId}/accounts/**}) are fully
 * covered by the {@link AuthorizationInterceptor} auth policy.
 *
 * <p>Policy rules verified:
 * <ul>
 *   <li>Unauthenticated requests pass through (Spring Security filter chain returns 401).</li>
 *   <li>Authenticated users must carry {@code ROLE_USER} or {@code ROLE_ADMIN}.</li>
 *   <li>Mutating operations (POST, DELETE) require {@code ROLE_ADMIN}.</li>
 * </ul>
 *
 * <p>Uses the same direct-interceptor pattern as {@code SecurityControllerTest} — no Spring
 * context needed, fast and deterministic.
 */
class AccountAuthorizationInterceptorTest {

    private static final String LIST_PATH   = "/api/v1/users/42/accounts";
    private static final String DETAIL_PATH = "/api/v1/users/42/accounts/7";

    private final AuthorizationInterceptor interceptor = new AuthorizationInterceptor();
    private final MockHttpServletResponse  response    = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── Unauthenticated ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unauthenticated — passed through (Spring Security returns 401)")
    class Unauthenticated {

        @Test
        void getAccounts_noAuth_passesThrough() {
            var request = new MockHttpServletRequest("GET", LIST_PATH);
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void postAccount_noAuth_passesThrough() {
            var request = new MockHttpServletRequest("POST", LIST_PATH);
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void deleteAccount_noAuth_passesThrough() {
            var request = new MockHttpServletRequest("DELETE", DETAIL_PATH);
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }
    }

    // ─── ROLE_USER ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ROLE_USER — read allowed, writes rejected")
    class RoleUser {

        @Test
        void getAccounts_roleUser_allowed() {
            withRole("ROLE_USER");
            var request = new MockHttpServletRequest("GET", LIST_PATH);
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void getAccountById_roleUser_allowed() {
            withRole("ROLE_USER");
            var request = new MockHttpServletRequest("GET", DETAIL_PATH);
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void postAccount_roleUser_forbidden() {
            withRole("ROLE_USER");
            var request = new MockHttpServletRequest("POST", LIST_PATH);
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preHandle(request, response, new Object()),
                    "POST requires ROLE_ADMIN — ROLE_USER alone must be rejected");
        }

        @Test
        void deleteAccount_roleUser_forbidden() {
            withRole("ROLE_USER");
            var request = new MockHttpServletRequest("DELETE", DETAIL_PATH);
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preHandle(request, response, new Object()),
                    "DELETE requires ROLE_ADMIN — ROLE_USER alone must be rejected");
        }
    }

    // ─── ROLE_ADMIN ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ROLE_ADMIN — all operations allowed")
    class RoleAdmin {

        @Test
        void getAccounts_roleAdmin_allowed() {
            withRole("ROLE_ADMIN");
            var request = new MockHttpServletRequest("GET", LIST_PATH);
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void postAccount_roleAdmin_allowed() {
            withRole("ROLE_ADMIN");
            var request = new MockHttpServletRequest("POST", LIST_PATH);
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void deleteAccount_roleAdmin_allowed() {
            withRole("ROLE_ADMIN");
            var request = new MockHttpServletRequest("DELETE", DETAIL_PATH);
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }
    }

    // ─── Unknown role ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown role — all operations rejected")
    class UnknownRole {

        @Test
        void getAccounts_unknownRole_forbidden() {
            withRole("ROLE_VIEWER");
            var request = new MockHttpServletRequest("GET", LIST_PATH);
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preHandle(request, response, new Object()));
        }

        @Test
        void postAccount_unknownRole_forbidden() {
            withRole("ROLE_VIEWER");
            var request = new MockHttpServletRequest("POST", LIST_PATH);
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preHandle(request, response, new Object()));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void withRole(String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("user", "n/a", role));
    }
}

