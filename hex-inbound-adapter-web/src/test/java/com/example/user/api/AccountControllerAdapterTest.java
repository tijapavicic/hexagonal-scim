package com.example.user.api;

import com.example.user.api.dto.CreateAccountRequest;
import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import com.example.user.port.in.TopUpAccountPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.math.BigDecimal;
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
    @Mock private TopUpAccountPort topUpAccountPort;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AccountControllerAdapter controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AccountControllerAdapter(
                createAccountPort,
                getUserAccountsPort,
                getAccountPort,
                deleteAccountPort,
                topUpAccountPort,
                meterRegistry
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
        when(createAccountPort.create(1L, "Main account")).thenReturn(new Account(10L, 1L, "Main account", BigDecimal.ZERO));

        var response = controller.create(1L, new CreateAccountRequest("Main account"));

        assertEquals(10L, response.id());
        assertEquals(1L, response.userId());
        assertEquals("Main account", response.name());
        assertEquals(BigDecimal.ZERO, response.balance());
        verify(createAccountPort).create(1L, "Main account");
    }

    @Test
    void getAllByUserIdDelegatesToPort() {
        when(getUserAccountsPort.getAllByUserId(1L))
                .thenReturn(List.of(
                        new Account(1L, 1L, "Main", new BigDecimal("10.00")),
                        new Account(2L, 1L, "Savings", new BigDecimal("20.00"))
                ));

        var response = controller.getAllByUserId(1L);

        assertEquals(2, response.size());
        verify(getUserAccountsPort).getAllByUserId(1L);
    }

    @Test
    void topUpDelegatesToPortAndReturnsUpdatedBalance() {
        when(topUpAccountPort.topUp(1L, 10L, new BigDecimal("100.00")))
                .thenReturn(new Account(10L, 1L, "Main", new BigDecimal("100.00")));

        var response = controller.topUp(1L, 10L, new com.example.user.api.dto.TopUpAccountRequest(new BigDecimal("100.00")));

        assertEquals(10L, response.id());
        assertEquals(new BigDecimal("100.00"), response.balance());
        verify(topUpAccountPort).topUp(1L, 10L, new BigDecimal("100.00"));
    }

    @Test
    void topUpReturns400WhenAmountIsZero() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users/{userId}/accounts/{accountId}/topup", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"code\":\"VALIDATION_ERROR\""));
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

