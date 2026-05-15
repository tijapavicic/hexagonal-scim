package com.example.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for payment HTTP flow. These tests intentionally live in the application
 * module to keep framework bootstrapping separate from core business logic unit tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class PaymentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    void createPaymentInUsdAndReadBackById() throws Exception {
        long userId = 1L;
        String email = getUserEmailViaApi(userId);
        long accountId = createAccountViaApi(userId, "payment-main");

        mockMvc.perform(post("/api/v1/users/{userId}/accounts/{accountId}/topup", userId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .with(authenticatedUser(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "USD"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(87.99))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentMethod").value("PAYPAL"))
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long paymentId = body.path("id").asLong();

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(87.99));
    }

    @Test
    void createPaymentReturnsBadRequestForUnsupportedCurrency() throws Exception {
        long userId = 1L;
        String email = getUserEmailViaApi(userId);
        long accountId = createAccountViaApi(userId, "payment-gbp");

        mockMvc.perform(post("/api/v1/users/{userId}/accounts/{accountId}/topup", userId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments")
                        .with(authenticatedUser(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "GBP"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
    }

    @Test
    void topUpThenPayReducesBalance() throws Exception {
        long userId = 1L;
        String email = getUserEmailViaApi(userId);
        long accountId = createAccountViaApi(userId, "flow-topup-pay-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/users/{userId}/accounts/{accountId}/topup", userId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(post("/api/v1/payments")
                        .with(authenticatedUser(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "EUR"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAmount").value(79.99));

        mockMvc.perform(get("/api/v1/users/{userId}/accounts/{accountId}", userId, accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(20.01));
    }

    @Test
    void insufficientFundsReturns422AndBalanceUnchanged() throws Exception {
        long userId = 1L;
        String email = getUserEmailViaApi(userId);
        long accountId = createAccountViaApi(userId, "flow-insufficient-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/users/{userId}/accounts/{accountId}/topup", userId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.00));

        mockMvc.perform(post("/api/v1/payments")
                        .with(authenticatedUser(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "EUR"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mockMvc.perform(get("/api/v1/users/{userId}/accounts/{accountId}", userId, accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.00));
    }

    @Test
    void accountBoundPaymentWithoutResolvableJwtEmailReturns400() throws Exception {
        long accountId = createAccountViaApi(1L, "flow-unresolvable-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/payments")
                        .with(authenticatedUser("not-found-" + System.nanoTime() + "@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "EUR"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private long createAccountViaApi(Long userId, String name) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/users/{userId}/accounts", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asLong();
    }

    private String getUserEmailViaApi(long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("email").asText();
    }

    private RequestPostProcessor authenticatedUser(String email) {
        return jwt()
                .jwt(jwt -> jwt.claim("email", email))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}

