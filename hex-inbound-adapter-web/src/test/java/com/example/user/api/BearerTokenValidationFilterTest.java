package com.example.user.api;

import com.example.user.api.config.BearerTokenValidationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link BearerTokenValidationFilter}.
 *
 * <p>Uses Spring's {@link MockHttpServletRequest}/{@link MockHttpServletResponse} and
 * {@link MockFilterChain} — no Spring context required.
 */
class BearerTokenValidationFilterTest {

    private BearerTokenValidationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new BearerTokenValidationFilter(objectMapper);
        MDC.clear();
    }

    // ── Missing header ───────────────────────────────────────────────────────────

    @Test
    void missingAuthorizationHeader_returns401WithJsonBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("MISSING_AUTHORIZATION_HEADER");
        assertThat(response.getContentAsString()).contains("Authorization header is required");
        // RFC 6750 §3.1 — WWW-Authenticate header must be present
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer realm=\"api\"");
        // Chain must NOT have been invoked
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void blankAuthorizationHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_AUTHORIZATION_HEADER");
    }

    // ── Wrong scheme ─────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Token abc123", "bearer abc123", "BEARER abc123"})
    void nonBearerScheme_returns401WithInvalidSchemeCode(String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", headerValue);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_AUTHORIZATION_HEADER");
        assertThat(response.getContentAsString()).contains("Bearer scheme");
    }

    // ── Empty token after "Bearer " ──────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"Bearer ", "Bearer    "})
    void emptyBearerToken_returns401WithEmptyTokenCode(String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", headerValue);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("EMPTY_BEARER_TOKEN");
    }

    // ── Valid Bearer token — chain continues ─────────────────────────────────────

    @Test
    void validBearerToken_chainContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.test.sig");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Status is 200 (default) — filter did not write a response
        assertThat(response.getStatus()).isEqualTo(200);
        // Chain was invoked (MockFilterChain records the last dispatched request)
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Public paths — no header required ────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator/health",
        "/actuator/info",
        "/swagger-ui/index.html",
        "/swagger-ui.html",
        "/api-docs/swagger-config"
    })
    void publicPath_noAuthorizationHeaderRequired(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        // NO Authorization header
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── MDC population ───────────────────────────────────────────────────────────

    @Test
    void correlationIdHeader_populatesMdcDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Correlation-ID", "corr-abc-123");
        request.addHeader("X-Request-ID", "req-xyz-456");

        // FilterChain is a functional interface — use lambda to capture MDC mid-chain
        final String[] capturedCorr  = new String[1];
        final String[] capturedReqId = new String[1];
        FilterChain chain = (req, res) -> {
            capturedCorr[0]  = MDC.get("correlationId");
            capturedReqId[0] = MDC.get("requestId");
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedCorr[0]).isEqualTo("corr-abc-123");
        assertThat(capturedReqId[0]).isEqualTo("req-xyz-456");
    }

    @Test
    void missingRequestIdHeader_serverGeneratesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        // NO X-Request-ID header

        final String[] capturedReqId = new String[1];
        FilterChain chain = (req, res) -> capturedReqId[0] = MDC.get("requestId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedReqId[0])
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void missingCorrelationIdHeader_mdcKeyAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        // NO X-Correlation-ID

        final String[] capturedCorr = new String[1];
        FilterChain chain = (req, res) -> capturedCorr[0] = MDC.get("correlationId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedCorr[0]).isNull();
    }

    @Test
    void mdcClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Correlation-ID", "corr-abc");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // MDC must be cleared in the finally block
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
    }

    // ── Error response is valid JSON with correct envelope ───────────────────────

    @Test
    void errorResponse_isValidJsonWithAllEnvelopeFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        var node = objectMapper.readTree(response.getContentAsString());
        assertThat(node.has("code")).isTrue();
        assertThat(node.has("message")).isTrue();
        assertThat(node.has("path")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.get("path").asText()).isEqualTo("/api/v1/users");
    }

    // ── POST and write methods also checked ──────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void writeMethods_missingHeader_returns401(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_AUTHORIZATION_HEADER");
    }

    // ── No exception thrown on chain error ───────────────────────────────────────

    @Test
    void doesNotThrowWhenFilterIsConstructed() {
        assertDoesNotThrow(() -> new BearerTokenValidationFilter(new ObjectMapper()));
    }
}

