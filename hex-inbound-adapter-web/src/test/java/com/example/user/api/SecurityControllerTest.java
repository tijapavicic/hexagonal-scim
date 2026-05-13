package com.example.user.api;

import com.example.user.api.config.AuthorizationInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityControllerTest {

    private final AuthorizationInterceptor interceptor = new AuthorizationInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestIsPassedThrough() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
    }

    @Test
    void readRequestAllowsRoleUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "n/a", "ROLE_USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        assertDoesNotThrow(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void writeRequestRejectsRoleUserWithoutAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "n/a", "ROLE_USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void requestRejectsUnknownRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "n/a", "ROLE_UNKNOWN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}

