package com.example.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-level security checks for protected API behavior in secured mode.
 */
@SpringBootTest(properties = {
        // Activates JWT resource server security chain in test runtime.
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/mock-jwks"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class ApiSecurityContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/users returns 401 when no token is provided")
    void getUsersWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/users returns 403 for authenticated ROLE_USER (non-admin)")
    void createUserWithRoleUserReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "role-user-contract@example.com",
                                  "displayName": "Role User Contract"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}

