package com.example.user.api;

import com.example.user.config.LegacyApiDeprecationProperties;
import com.example.user.core.UserNotFoundException;
import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserControllerAdapter.class)
@Import({ApiExceptionHandlerAdapter.class, WebAdapterTestApplication.class, LegacyApiDeprecationProperties.class})
@TestPropertySource(properties = {
        "api.legacy.deprecation-value=deprecated",
        "api.legacy.sunset-date=Thu, 01 Jan 2027 00:00:00 GMT",
        "api.legacy.successor-link=</api/v2/users>; rel=\"successor-version\""
})
class UserControllerAdapterTest {
    private static final String VERSIONED_USERS_PATH = "/api/v1/users";
    private static final String LEGACY_USERS_PATH = "/api/users";
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";
    private static final String LINK_HEADER = "Link";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegacyApiDeprecationProperties legacyApiDeprecationProperties;

    @MockBean
    private CreateUserPort createUserPort;

    @MockBean
    private GetUserPort getUserPort;

    @MockBean
    private GetAllUsersPort getAllUsersPort;

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

        verify(getAllUsersPort).getAll(0, 10, true);
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



