package com.example.user.api;

import com.example.user.core.UserNotFoundException;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserUseCase;
import com.example.user.port.in.GetUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({ApiExceptionHandler.class, WebAdapterTestApplication.class})
class UserControllerTest {
    private static final String VERSIONED_USERS_PATH = "/api/v1/users";
    private static final String LEGACY_USERS_PATH = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateUserUseCase createUserUseCase;

    @MockBean
    private GetUserUseCase getUserUseCase;

    @Test
    void createReturnsCreatedUser() throws Exception {
        when(createUserUseCase.create(anyString(), anyString()))
                .thenReturn(new User(1L, "john@example.com", "John"));

        mockMvc.perform(post(VERSIONED_USERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@example.com\",\"displayName\":\"John\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post(VERSIONED_USERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        when(getUserUseCase.getById(eq(999L))).thenThrow(new UserNotFoundException("missing"));

        mockMvc.perform(get(VERSIONED_USERS_PATH + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void legacyPathIsStillSupported() throws Exception {
        when(getUserUseCase.getById(eq(999L))).thenThrow(new UserNotFoundException("missing"));

        mockMvc.perform(get(LEGACY_USERS_PATH + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
