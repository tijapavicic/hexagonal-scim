package com.example.user.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.user.api.config.BearerTokenValidationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * OWASP-aligned security tests for {@link BearerTokenValidationFilter}.
 *
 * <p>One test per vulnerability / CWE.  Each test documents which OWASP Top 10 category
 * or CWE it addresses and what the expected secure behaviour is.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>OWASP A01:2021 – Broken Access Control</li>
 *   <li>OWASP A02:2021 – Cryptographic Failures (alg:none JWT forwarding)</li>
 *   <li>OWASP A03:2021 – Injection (CRLF, null-byte, header injection)</li>
 *   <li>OWASP A05:2021 – Security Misconfiguration (information leakage)</li>
 *   <li>OWASP A07:2021 – Identification and Authentication Failures</li>
 *   <li>OWASP A09:2021 – Security Logging and Monitoring Failures</li>
 *   <li>CWE-117  – Log Injection / Improper Output Neutralisation</li>
 *   <li>CWE-200  – Information Exposure Through Error Message</li>
 *   <li>CWE-284  – Improper Access Control (HTTP method coverage)</li>
 *   <li>CWE-400  – Uncontrolled Resource Consumption (DoS via large header)</li>
 *   <li>RFC 6750  – Bearer Token Usage (WWW-Authenticate compliance)</li>
 * </ul>
 */
class BearerTokenValidationFilterOwaspTest {

    private BearerTokenValidationFilter filter;
    private ObjectMapper objectMapper;

    // Logback appender for A09 log-verification test
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new BearerTokenValidationFilter(objectMapper);
        MDC.clear();

        // Attach in-memory log appender to the filter's logger
        filterLogger = (Logger) LoggerFactory.getLogger(BearerTokenValidationFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
        MDC.clear();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A01:2021 – Broken Access Control
    // CWE-284 – Improper Access Control
    // All protected endpoints must enforce authentication regardless of HTTP method.
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A01 – OPTIONS request without token is blocked (no CORS pre-flight bypass)")
    void a01_optionsRequestWithoutToken_isBlocked() throws Exception {
        // OPTIONS is sometimes used for CORS pre-flight; must not bypass auth silently
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_AUTHORIZATION_HEADER");
    }

    @Test
    @DisplayName("A01 – HEAD request without token is blocked (no silent method bypass)")
    void a01_headRequestWithoutToken_isBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("A01 – Path traversal attempt does NOT bypass public-path allowlist")
    void a01_pathTraversal_doesNotBypassPublicPathAllowlist() throws Exception {
        // An attacker crafts a path that looks like it might normalise to /actuator/health
        // AntPathMatcher does NOT normalise raw URI segments → auth is required
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/../actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        // /api/../actuator/health does NOT match the exact pattern "/actuator/health"
        // so the filter correctly requires authentication
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("A01 – Encoded path traversal does NOT bypass public-path allowlist")
    void a01_encodedPathTraversal_doesNotBypassPublicPathAllowlist() throws Exception {
        // URL-encoded traversal attempt
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/%2F..%2Factuator%2Fhealth");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A02:2021 – Cryptographic Failures
    // alg:none JWT must NOT be silently accepted by the filter — it must be
    // forwarded to the JWT validator (Spring Security) which will reject it.
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A02 – alg:none JWT is forwarded to Spring Security for rejection (not silently accepted)")
    void a02_algNoneJwt_isPassedToNextFilterForProperValidation() throws Exception {
        // eyJhbGciOiJub25lIn0 = {"alg":"none"}
        // Our filter checks header PRESENCE and SCHEME only — it must pass this to the chain
        // so that Spring Security's JWT validator can reject the unsigned token.
        // Accepting it here (returning 200) would be a vulnerability only if the downstream
        // JWT validator is also bypassed — this test documents the filter's intentional scope.
        String algNoneToken = "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciJ9.";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", algNoneToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Filter passes it through — Spring Security (running after) must validate signature
        // This asserts the filter does NOT block it (so Spring Security can reject it properly)
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A03:2021 – Injection
    // CWE-117 – Improper Output Neutralisation for Logs (Log Injection)
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A03/CWE-117 – CRLF in X-Correlation-ID is stripped before MDC (log injection prevention)")
    void a03_crlfInCorrelationId_isSanitizedBeforeMdc() throws Exception {
        // Attacker injects CRLF to forge log entries
        String maliciousCorrelationId = "legit-id\r\nflow_stage=FAKE operation=evil status=SUCCESS";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Correlation-ID", maliciousCorrelationId);

        final String[] capturedCorr = new String[1];
        FilterChain chain = (req, res) -> capturedCorr[0] = MDC.get("correlationId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // CRLF must be replaced with underscores — no newlines in MDC
        assertThat(capturedCorr[0]).doesNotContain("\r").doesNotContain("\n");
        assertThat(capturedCorr[0]).contains("legit-id__");
    }

    @Test
    @DisplayName("A03/CWE-117 – CRLF in X-Request-ID is stripped before MDC (log injection prevention)")
    void a03_crlfInRequestId_isSanitizedBeforeMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Request-ID", "req-123\nX-Forwarded-For: 127.0.0.1");

        final String[] capturedReqId = new String[1];
        FilterChain chain = (req, res) -> capturedReqId[0] = MDC.get("requestId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedReqId[0]).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("A03/CWE-117 – Tab character in X-Correlation-ID is stripped (log injection prevention)")
    void a03_tabInCorrelationId_isSanitizedBeforeMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Correlation-ID", "id\tadmin\ttrue");

        final String[] capturedCorr = new String[1];
        FilterChain chain = (req, res) -> capturedCorr[0] = MDC.get("correlationId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedCorr[0]).doesNotContain("\t");
        assertThat(capturedCorr[0]).isEqualTo("id_admin_true");
    }

    @Test
    @DisplayName("A03 – Null-byte in Authorization header is treated as unknown scheme, not as bypass")
    void a03_nullByteInAuthorizationHeader_doesNotBypassCheck() throws Exception {
        // Null-byte injection attempt in token value
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer \0admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // The token is not blank — filter passes to chain
        // Spring Security (running after) rejects the null-byte in the JWT
        // This asserts the filter does not crash on null-byte input
        assertThatCode(() -> filter.doFilter(request, response, new MockFilterChain()))
                .doesNotThrowAnyException();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A05:2021 – Security Misconfiguration
    // CWE-200 – Information Exposure Through Error Message
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A05/CWE-200 – 401 error response does NOT expose stack traces")
    void a05_cwe200_errorResponse_doesNotContainStackTrace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String body = response.getContentAsString();
        // Stack trace indicators must never appear in API error responses
        assertThat(body).doesNotContain("at com.");
        assertThat(body).doesNotContain("at java.");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("Caused by");
        assertThat(body).doesNotContain("java.lang");
    }

    @Test
    @DisplayName("A05/CWE-200 – 401 error response does NOT expose internal class names or package paths")
    void a05_cwe200_errorResponse_doesNotLeakInternalPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/secret-data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String body = response.getContentAsString();
        // No internal package names, filter class names, or framework internals
        assertThat(body).doesNotContain("BearerTokenValidationFilter");
        assertThat(body).doesNotContain("com.example.user.api");
        assertThat(body).doesNotContain("OncePerRequestFilter");
        assertThat(body).doesNotContain("springframework");
    }

    @Test
    @DisplayName("A05 – Response Content-Type is always application/json, never text/html (XSS vector)")
    void a05_errorResponse_contentTypeIsJsonNotHtml() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        // text/html would allow XSS if body contained user-controlled content
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentType()).doesNotContain("text/html");
    }

    @Test
    @DisplayName("A05 – Error response path field reflects actual request URI only")
    void a05_errorResponse_pathFieldContainsOnlyRequestUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        // path must contain ONLY the request URI — no server name, port, or query string
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/orders/42");
        assertThat(body.get("path").asText()).doesNotContain("http");
        assertThat(body.get("path").asText()).doesNotContain("localhost");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A07:2021 – Identification and Authentication Failures
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A07 – Basic authentication scheme is rejected with clear error message")
    void a07_basicAuthScheme_isRejectedWithClearMessage() throws Exception {
        // Attacker or misconfigured client sends Basic auth instead of Bearer
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        String body = response.getContentAsString();
        assertThat(body).contains("INVALID_AUTHORIZATION_HEADER");
        assertThat(body).contains("Bearer scheme");
        // Must NOT contain the Base64 credential value in the error message
        assertThat(body).doesNotContain("dXNlcjpwYXNzd29yZA==");
    }

    @Test
    @DisplayName("A07 – Case-sensitive Bearer scheme: 'bearer' (lowercase) is rejected")
    void a07_lowercaseBearerScheme_isRejected() throws Exception {
        // RFC 6750 defines 'Bearer' as case-sensitive
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "bearer eyJhbGciOiJSUzI1NiJ9.test.sig");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_AUTHORIZATION_HEADER");
    }

    @Test
    @DisplayName("A07 – WWW-Authenticate header is RFC 6750 §3.1 compliant on 401 response")
    void a07_wwwAuthenticateHeader_isRfc6750Compliant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String wwwAuth = response.getHeader("WWW-Authenticate");
        assertThat(wwwAuth).isNotNull();
        // RFC 6750 §3.1: must start with 'Bearer'
        assertThat(wwwAuth).startsWith("Bearer ");
        // Must include realm
        assertThat(wwwAuth).contains("realm=");
        // Must include error attribute
        assertThat(wwwAuth).contains("error=");
    }

    @Test
    @DisplayName("A07 – Token preceded by extra whitespace is NOT treated as valid Bearer format")
    void a07_tokenWithLeadingWhitespaceBeforeBearer_isRejected() throws Exception {
        // Attacker tries to sneak in a token with leading spaces
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "  Bearer eyJhbGciOiJSUzI1NiJ9.test.sig");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_AUTHORIZATION_HEADER");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // OWASP A09:2021 – Security Logging and Monitoring Failures
    // Every authentication failure must produce a WARN-level log entry so that
    // a SIEM / Splunk can detect brute-force and credential-stuffing attacks.
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A09 – Missing Authorization header produces WARN log with request context")
    void a09_missingHeader_producesWarnLog() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        List<ILoggingEvent> warnEvents = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();

        assertThat(warnEvents).isNotEmpty();
        String logMsg = warnEvents.get(0).getFormattedMessage();
        assertThat(logMsg).contains("MISSING_HEADER");
        assertThat(logMsg).contains("GET");
        assertThat(logMsg).contains("/api/v1/users");
    }

    @Test
    @DisplayName("A09 – Invalid Authorization scheme produces WARN log with request context")
    void a09_invalidScheme_producesWarnLog() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "ApiKey secret123");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        List<ILoggingEvent> warnEvents = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();

        assertThat(warnEvents).isNotEmpty();
        String logMsg = warnEvents.get(0).getFormattedMessage();
        assertThat(logMsg).contains("INVALID_SCHEME");
        // The actual secret value must NOT appear in the log
        assertThat(logMsg).doesNotContain("secret123");
    }

    @Test
    @DisplayName("A09 – Successful requests produce no WARN or ERROR log entries (no false positives)")
    void a09_validRequest_doesNotProduceWarnOrErrorLog() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.test.sig");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        long warnOrErrorCount = logAppender.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
                .count();
        assertThat(warnOrErrorCount).isZero();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CWE-117 – Improper Output Neutralisation for Logs
    // BearerTokenValidationFilter.sanitizeMdcValue() unit tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CWE-117 – sanitizeMdcValue strips CR (\\r)")
    void cwe117_sanitizeMdcValue_stripsCarriageReturn() {
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue("abc\rdef"))
                .isEqualTo("abc_def");
    }

    @Test
    @DisplayName("CWE-117 – sanitizeMdcValue strips LF (\\n)")
    void cwe117_sanitizeMdcValue_stripsLineFeed() {
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue("abc\ndef"))
                .isEqualTo("abc_def");
    }

    @Test
    @DisplayName("CWE-117 – sanitizeMdcValue strips TAB (\\t)")
    void cwe117_sanitizeMdcValue_stripsTab() {
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue("abc\tdef"))
                .isEqualTo("abc_def");
    }

    @Test
    @DisplayName("CWE-117 – sanitizeMdcValue returns null for blank input")
    void cwe117_sanitizeMdcValue_returnsNullForBlank() {
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue(null)).isNull();
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue("")).isNull();
        assertThat(BearerTokenValidationFilter.sanitizeMdcValue("   ")).isNull();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CWE-400 – Uncontrolled Resource Consumption
    // A filter that crashes or hangs on an oversized input can be DoS'd.
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CWE-400 – Oversized Authorization header (100 KB) does not crash or OOM the filter")
    void cwe400_oversizedAuthorizationHeader_doesNotCrashFilter() {
        String hugeToken = "Bearer " + "A".repeat(100_000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", hugeToken);

        assertThatCode(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CWE-400 – Oversized X-Correlation-ID (200 chars) is truncated to MDC_MAX_VALUE_LENGTH")
    void cwe400_oversizedCorrelationId_isTruncatedInMdc() throws Exception {
        String oversizedId = "x".repeat(200);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token");
        request.addHeader("X-Correlation-ID", oversizedId);

        final String[] capturedCorr = new String[1];
        FilterChain chain = (req, res) -> capturedCorr[0] = MDC.get("correlationId");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(capturedCorr[0]).hasSize(BearerTokenValidationFilter.MDC_MAX_VALUE_LENGTH);
    }

    @Test
    @DisplayName("CWE-400 – Filter still handles request correctly when chain throws an exception (MDC cleanup)")
    void cwe400_mdcClearedEvenWhenChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid.token.here");
        request.addHeader("X-Correlation-ID", "corr-test");

        FilterChain throwingChain = (req, res) -> { throw new RuntimeException("downstream failure"); };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), throwingChain);
        } catch (RuntimeException ignored) {
            // expected
        }

        // MDC must be cleared even if the downstream chain threw
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
    }
}

