package com.example.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring Boot context smoke tests (H2 + NoSecurityConfig — no auth required).
 *
 * <p>These tests verify end-to-end wiring: routing → controller → service → repository → H2.
 * The V3 migration seeds exactly 25 users which are used for pagination assertions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class HexagonalScimApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    // ─── context ──────────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Verifies all beans wired correctly (UserConfig, UserService, adapters, security)
    }

    // ─── actuator ─────────────────────────────────────────────────────────────

    @Test
    void actuatorHealthReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorPrometheusEndpointIsExposed() throws Exception {
        // Verify Micrometer Prometheus endpoint was wired by the new dependency
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    // ─── Swagger / OpenAPI ────────────────────────────────────────────────────

    @Test
    void swaggerUiRedirectsToIndexPage() throws Exception {
        // /swagger-ui.html redirects to /swagger-ui/index.html (springdoc behaviour)
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiSpecIsAccessible() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info").exists())
                .andExpect(jsonPath("$.paths").exists());
    }

    // ─── primary API (seeded data from V3 migration) ──────────────────────────

    @Test
    void getAllWithPageableFalseReturnsAllSeedData() throws Exception {
        // V3 migration inserts exactly 25 sample users — all must be returned when pageable=false
        mockMvc.perform(get("/api/v1/users").param("pageable", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(25))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void getAllDefaultReturnsFirstPageOfTen() throws Exception {
        // Default pageable=true with size=10 must return only 10 of the 25 seeded users
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void getAllWithCustomPageAndSizeReturnsCorrectSlice() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void getByIdReturns200ForSeedUser() throws Exception {
        // Seed user with id=1 inserted by V3 migration
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.displayName").isNotEmpty());
    }

    @Test
    void getByIdReturns404ForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/999999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // ─── legacy path ─────────────────────────────────────────────────────────

    @Test
    void legacyPathReturnsDataWithDeprecationHeaders() throws Exception {
        mockMvc.perform(get("/api/users").param("pageable", "false"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().exists("Sunset"))
                .andExpect(header().exists("Link"))
                .andExpect(jsonPath("$.content.length()").value(25));
    }
}

