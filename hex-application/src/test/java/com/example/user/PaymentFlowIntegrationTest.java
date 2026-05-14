package com.example.user;

import com.example.user.adapter.db.AccountEntity;
import com.example.user.adapter.db.AccountJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Test
    void createPaymentInUsdAndReadBackById() throws Exception {
        Long accountId = createFundedAccount(1L, "payment-main", new java.math.BigDecimal("500.00"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "accountId": %d,
                                  "productId": 1,
                                  "quantity": 1,
                                  "paymentMethod": "PAYPAL",
                                  "currency": "USD"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
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
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(87.99));
    }

    @Test
    void createPaymentReturnsBadRequestForUnsupportedCurrency() throws Exception {
        Long accountId = createFundedAccount(1L, "payment-gbp", new java.math.BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
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

    private Long createFundedAccount(Long userId, String name, java.math.BigDecimal balance) {
        AccountEntity saved = accountJpaRepository.save(new AccountEntity(userId, name, balance));
        return saved.getId();
    }
}

