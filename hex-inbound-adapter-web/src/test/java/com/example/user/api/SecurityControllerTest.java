package com.example.user.api;

import com.example.user.api.config.ApiPaginationProperties;
import com.example.user.api.config.AuthorizationInterceptor;
import com.example.user.api.config.LegacyApiDeprecationProperties;
import com.example.user.api.config.SecurityConfig;
import com.example.user.api.config.WebMvcConfig;
import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.UpdateUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for the REST layer.
 *
 * <p>Security filters are <strong>enabled</strong> (no {@code addFilters = false}) so that
 * the real {@link SecurityConfig} + {@link AuthorizationInterceptor} are exercised.
 *
 * <p>A mock {@link JwtDecoder} is provided to prevent the auto-configuration from
 * attempting a live OIDC discovery call to Keycloak.
 *
 * <h2>Scenarios covered</h2>
 * <ul>
 *   <li>401 — no {@code Authorization} header at all</li>
 *   <li>403 — authenticated as {@code ROLE_USER} trying a write operation (POST/PUT/PATCH/DELETE)</li>
 *   <li>200 — authenticated with {@code ROLE_USER} doing a read (GET)</li>
 *   <li>201 — authenticated with {@code ROLE_ADMIN} doing a create (POST)</li>
 *   <li>204 — authenticated with {@code ROLE_ADMIN} doing a delete (DELETE)</li>
 * </ul>
 */
@WebMvcTest(controllers = UserControllerAdapter.class)
@Import({
        SecurityConfig.class,
        ApiExceptionHandlerAdapter.class,
        WebAdapterTestApplication.class,
        LegacyApiDeprecationProperties.class,
        ApiPaginationProperties.class,
        WebMvcConfig.class,
        AuthorizationInterceptor.class
})
@TestPropertySource(properties = {
        // Activates SecurityConfig (condition checks for non-empty issuer-uri or jwk-set-uri)
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/test",
        "api.legacy.deprecation-value=deprecated",
        "api.legacy.sunset-date=Thu, 01 Jan 2027 00:00:00 GMT",
        "api.legacy.successor-link=</api/v2/users>; rel=\"successor-version\"",
        "api.pagination.default-page=0",
        "api.pagination.default-size=10",
        "api.pagination.default-pageable=true"
})
class SecurityControllerTest {

    private static final String USERS_PATH = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock the JwtDecoder so that SecurityConfig does not perform a live discovery
     * call to the (non-existent) test issuer URI.
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean private CreateUserPort createUserPort;
    @MockBean private GetUserPort getUserPort;
    @MockBean private GetAllUsersPort getAllUsersPort;
    @MockBean private UpdateUserPort updateUserPort;
    @MockBean private PatchUserPort patchUserPort;
    @MockBean private DeleteUserPort deleteUserPort;

    // ─── 401 Unauthorized ────────────────────────────────────────────────────

    @Test
    void returns401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get(USERS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns401WhenNoTokenForWriteOperation() throws Exception {
        mockMvc.perform(post(USERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"displayName\":\"A\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── 403 Forbidden (wrong role) ───────────────────────────────────────────

    @Test
    void returns403WhenUserRoleTriesPost() throws Exception {
        mockMvc.perform(post(USERS_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"displayName\":\"Test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returns403WhenUserRoleTriesPut() throws Exception {
        mockMvc.perform(put(USERS_PATH + "/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"displayName\":\"Test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returns403WhenUserRoleTriesPatch() throws Exception {
        mockMvc.perform(patch(USERS_PATH + "/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returns403WhenUserRoleTriesDelete() throws Exception {
        mockMvc.perform(delete(USERS_PATH + "/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returns403WhenTokenHasNoRecognisedRole() throws Exception {
        mockMvc.perform(get(USERS_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_UNKNOWN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── Happy path — ROLE_USER (read-only) ───────────────────────────────────

    @Test
    void returns200WhenUserRoleReadsUsers() throws Exception {
        when(getAllUsersPort.getAll(0, 10, true))
                .thenReturn(new PagedUsers(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get(USERS_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void returns200WhenUserRoleGetById() throws Exception {
        when(getUserPort.getById(1L))
                .thenReturn(new User(1L, "alice@example.com", "Alice"));

        mockMvc.perform(get(USERS_PATH + "/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    // ─── Happy path — ROLE_ADMIN (full access) ────────────────────────────────

    @Test
    void returns201WhenAdminCreatesUser() throws Exception {
        when(createUserPort.create("alice@example.com", "Alice"))
                .thenReturn(new User(1L, "alice@example.com", "Alice"));

        mockMvc.perform(post(USERS_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"displayName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void returns204WhenAdminDeletesUser() throws Exception {
        mockMvc.perform(delete(USERS_PATH + "/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void returns200WhenAdminReadsUsers() throws Exception {
        when(getAllUsersPort.getAll(0, 10, true))
                .thenReturn(new PagedUsers(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get(USERS_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}

