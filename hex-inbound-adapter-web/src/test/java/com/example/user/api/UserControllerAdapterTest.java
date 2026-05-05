package com.example.user.api;

import com.example.user.api.config.ApiPaginationProperties;
import com.example.user.api.config.LegacyApiDeprecationProperties;
import com.example.user.core.DuplicateUserException;
import com.example.user.core.UserNotFoundException;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserControllerAdapter.class)
@Import({ApiExceptionHandlerAdapter.class, WebAdapterTestApplication.class, LegacyApiDeprecationProperties.class, ApiPaginationProperties.class})
@TestPropertySource(properties = {
        "api.legacy.deprecation-value=deprecated",
        "api.legacy.sunset-date=Thu, 01 Jan 2027 00:00:00 GMT",
        "api.legacy.successor-link=</api/v2/users>; rel=\"successor-version\"",
        "api.pagination.default-page=0",
        "api.pagination.default-size=10",
        "api.pagination.default-pageable=true"
})
class UserControllerAdapterTest {
    private static final String VERSIONED_USERS_PATH = "/api/v1/users";
    private static final String LEGACY_USERS_PATH = "/api/users";
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";
    private static final String LINK_HEADER = "Link";

    @Autowired private MockMvc mockMvc;
    @Autowired private LegacyApiDeprecationProperties legacyApiDeprecationProperties;
    @Autowired private ApiPaginationProperties apiPaginationProperties;

    @MockBean private CreateUserPort createUserPort;
    @MockBean private GetUserPort getUserPort;
    @MockBean private GetAllUsersPort getAllUsersPort;
    @MockBean private UpdateUserPort updateUserPort;
    @MockBean private PatchUserPort patchUserPort;
    @MockBean private DeleteUserPort deleteUserPort;

    @Test
    void createReturnsCreatedUser() throws Exception {
        when(createUserPort.create(anyString(), anyString()))
                .thenReturn(new User(1L, "john@example.com", "John"));

        ResultActions result = mockMvc.perform(post(VERSIONED_USERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@example.com\",\"displayName\":\"John\"}"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"));

        verify(createUserPort).create("john@example.com", "John");
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        ResultActions result = mockMvc.perform(post(VERSIONED_USERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"displayName\":\"\"}"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(createUserPort, getUserPort, getAllUsersPort);
    }

    @Test
    void getAllReturnsPagedUsers() throws Exception {
        User user1 = new User(1L, "alice@example.com", "Alice");
        User user2 = new User(2L, "bob@example.com", "Bob");
        PagedUsers pagedUsers = new PagedUsers(List.of(user1, user2), 0, 10, 2, 1);

        when(getAllUsersPort.getAll(0, 10, true)).thenReturn(pagedUsers);

        ResultActions result = mockMvc.perform(get(VERSIONED_USERS_PATH));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));

        // defaults sourced from ApiPaginationProperties, not hardcoded constants
        verify(getAllUsersPort).getAll(
                apiPaginationProperties.getDefaultPage(),
                apiPaginationProperties.getDefaultSize(),
                apiPaginationProperties.isDefaultPageable());
    }

    @Test
    void getAllWithPageableFalseReturnsAllUsers() throws Exception {
        User user1 = new User(1L, "alice@example.com", "Alice");
        User user2 = new User(2L, "bob@example.com", "Bob");
        User user3 = new User(3L, "charlie@example.com", "Charlie");
        PagedUsers allUsers = new PagedUsers(List.of(user1, user2, user3), 0, 3, 3, 1);

        when(getAllUsersPort.getAll(0, 10, false)).thenReturn(allUsers);

        ResultActions result = mockMvc.perform(get(VERSIONED_USERS_PATH).param("pageable", "false"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(getAllUsersPort).getAll(0, 10, false);
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        when(getUserPort.getById(eq(999L))).thenThrow(new UserNotFoundException("missing"));

        ResultActions result = mockMvc.perform(get(VERSIONED_USERS_PATH + "/999"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        verify(getUserPort).getById(999L);
    }

    @Test
    void legacyPathIsStillSupported() throws Exception {
        when(getUserPort.getById(eq(999L))).thenThrow(new UserNotFoundException("missing"));

        ResultActions result = mockMvc.perform(get(LEGACY_USERS_PATH + "/999"));

        assertLegacyHeaders(result);
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        verify(getUserPort).getById(999L);
    }

    // ─── PUT /{id} ────────────────────────────────────────────────────────────

    @Test
    void updateReturnsUpdatedUser() throws Exception {
        when(updateUserPort.update(1L, "new@example.com", "New"))
                .thenReturn(new User(1L, "new@example.com", "New"));

        ResultActions result = mockMvc.perform(put(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"displayName\":\"New\"}"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("New"));

        verify(updateUserPort).update(1L, "new@example.com", "New");
    }

    @Test
    void updateReturns404WhenUserNotFound() throws Exception {
        when(updateUserPort.update(eq(999L), anyString(), anyString()))
                .thenThrow(new UserNotFoundException("missing"));

        ResultActions result = mockMvc.perform(put(VERSIONED_USERS_PATH + "/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.com\",\"displayName\":\"X\"}"));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void updateReturns409OnDuplicateEmail() throws Exception {
        when(updateUserPort.update(eq(1L), anyString(), anyString()))
                .thenThrow(new DuplicateUserException("taken"));

        ResultActions result = mockMvc.perform(put(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"taken@example.com\",\"displayName\":\"X\"}"));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void updateRejectsInvalidPayload() throws Exception {
        ResultActions result = mockMvc.perform(put(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\",\"displayName\":\"\"}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(updateUserPort);
    }

    // ─── PATCH /{id} ──────────────────────────────────────────────────────────

    @Test
    void patchUpdatesDisplayNameOnly() throws Exception {
        when(patchUserPort.patch(1L, null, "Renamed"))
                .thenReturn(new User(1L, "alice@example.com", "Renamed"));

        ResultActions result = mockMvc.perform(patch(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Renamed\"}"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Renamed"));

        verify(patchUserPort).patch(1L, null, "Renamed");
    }

    @Test
    void patchUpdatesEmailOnly() throws Exception {
        when(patchUserPort.patch(1L, "new@example.com", null))
                .thenReturn(new User(1L, "new@example.com", "Alice"));

        ResultActions result = mockMvc.perform(patch(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\"}"));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        verify(patchUserPort).patch(1L, "new@example.com", null);
    }

    @Test
    void patchRejects400WhenNoFieldsProvided() throws Exception {
        ResultActions result = mockMvc.perform(patch(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(patchUserPort);
    }

    @Test
    void patchRejectsInvalidEmail() throws Exception {
        ResultActions result = mockMvc.perform(patch(VERSIONED_USERS_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(patchUserPort);
    }

    // ─── DELETE /{id} ─────────────────────────────────────────────────────────

    @Test
    void deleteReturns204OnSuccess() throws Exception {
        ResultActions result = mockMvc.perform(delete(VERSIONED_USERS_PATH + "/1"));

        assertNoLegacyHeaders(result);
        result.andExpect(status().isNoContent());

        verify(deleteUserPort).deleteById(1L);
    }

    @Test
    void deleteReturns404WhenUserNotFound() throws Exception {
        doThrow(new UserNotFoundException("missing")).when(deleteUserPort).deleteById(999L);

        ResultActions result = mockMvc.perform(delete(VERSIONED_USERS_PATH + "/999"));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── assertion helpers ────────────────────────────────────────────────────

    private void assertNoLegacyHeaders(ResultActions result) throws Exception {
        result.andExpect(header().doesNotExist(DEPRECATION_HEADER))
                .andExpect(header().doesNotExist(SUNSET_HEADER))
                .andExpect(header().doesNotExist(LINK_HEADER));
    }

    private void assertLegacyHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string(DEPRECATION_HEADER, legacyApiDeprecationProperties.getDeprecationValue()))
                .andExpect(header().string(SUNSET_HEADER, legacyApiDeprecationProperties.getSunsetDate()))
                .andExpect(header().string(LINK_HEADER, legacyApiDeprecationProperties.getSuccessorLink()));
    }
}



