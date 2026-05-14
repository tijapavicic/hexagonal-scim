package com.example.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void createAndReadUserAccountFlow() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Primary"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Primary"))
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long accountId = body.path("id").asLong();

        mockMvc.perform(get("/api/v1/users/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].userId").value(1));

        mockMvc.perform(get("/api/v1/users/1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Primary"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void topUpAccountUpdatesBalance() throws Exception {
        String accountName = "Topup-" + System.nanoTime();

        MvcResult createResult = mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + accountName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long accountId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/users/1/accounts/{accountId}/topup", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(get("/api/v1/users/1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void createAccountReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(post("/api/v1/users/999999/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Primary"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void createAccountReturns409ForDuplicateNameOnSameUser() throws Exception {
        String name = "Primary-dup-" + System.nanoTime();

        mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value(name));

        mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void createAccountAllowsSameNameForDifferentUsers() throws Exception {
        String sharedName = "Shared-" + System.nanoTime();

        mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + sharedName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value(sharedName));

        mockMvc.perform(post("/api/v1/users/2/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + sharedName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.name").value(sharedName));
    }

    @Test
    void deleteAccountRemovesEntryFromList() throws Exception {
        String firstName = "ToDelete-" + System.nanoTime();
        String secondName = "ToKeep-" + System.nanoTime();

        MvcResult firstCreate = mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + firstName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult secondCreate = mockMvc.perform(post("/api/v1/users/1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + secondName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long toDeleteId = objectMapper.readTree(firstCreate.getResponse().getContentAsString()).path("id").asLong();
        long toKeepId = objectMapper.readTree(secondCreate.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(delete("/api/v1/users/1/accounts/{accountId}", toDeleteId))
                .andExpect(status().isNoContent());

        MvcResult listResult = mockMvc.perform(get("/api/v1/users/1/accounts"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode accounts = objectMapper.readTree(listResult.getResponse().getContentAsString());
        boolean deletedStillPresent = false;
        boolean keptStillPresent = false;
        for (JsonNode account : accounts) {
            long id = account.path("id").asLong();
            if (id == toDeleteId) {
                deletedStillPresent = true;
            }
            if (id == toKeepId) {
                keptStillPresent = true;
            }
        }

        org.junit.jupiter.api.Assertions.assertFalse(deletedStillPresent, "deleted account should not be listed");
        org.junit.jupiter.api.Assertions.assertTrue(keptStillPresent, "non-deleted account should remain listed");
    }
}

