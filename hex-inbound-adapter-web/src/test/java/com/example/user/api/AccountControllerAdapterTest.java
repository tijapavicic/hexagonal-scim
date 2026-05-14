package com.example.user.api;

import com.example.user.api.dto.CreateAccountRequest;
import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerAdapterTest {

    @Mock private CreateAccountPort createAccountPort;
    @Mock private GetUserAccountsPort getUserAccountsPort;
    @Mock private GetAccountPort getAccountPort;
    @Mock private DeleteAccountPort deleteAccountPort;

    private AccountControllerAdapter controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AccountControllerAdapter(
                createAccountPort,
                getUserAccountsPort,
                getAccountPort,
                deleteAccountPort
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandlerAdapter())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createDelegatesToPortAndMapsResponse() {
        when(createAccountPort.create(1L, "Main account")).thenReturn(new Account(10L, 1L, "Main account"));

        var response = controller.create(1L, new CreateAccountRequest("Main account"));

        assertEquals(10L, response.id());
        assertEquals(1L, response.userId());
        assertEquals("Main account", response.name());
        verify(createAccountPort).create(1L, "Main account");
    }

    @Test
    void getAllByUserIdDelegatesToPort() {
        when(getUserAccountsPort.getAllByUserId(1L))
                .thenReturn(List.of(new Account(1L, 1L, "Main"), new Account(2L, 1L, "Savings")));

        var response = controller.getAllByUserId(1L);

        assertEquals(2, response.size());
        verify(getUserAccountsPort).getAllByUserId(1L);
    }

    @Test
    void createReturns400WhenNameIsBlank() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users/{userId}/accounts", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"code\":\"VALIDATION_ERROR\""));
        assertTrue(response.contains("\"message\":"));
    }

    @Test
    void createReturns400WhenNameIsMissing() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users/{userId}/accounts", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"code\":\"VALIDATION_ERROR\""));
        assertTrue(response.contains("\"message\":"));
    }

    @Test
    void createReturns400WhenNameExceedsMaxLength() throws Exception {
        String longName = "A".repeat(121);

        String response = mockMvc.perform(post("/api/v1/users/{userId}/accounts", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"code\":\"VALIDATION_ERROR\""));
        assertTrue(response.contains("\"message\":"));
    }
}

