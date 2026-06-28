package com.example.user.api.config;

import com.example.user.api.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter that validates the {@code Authorization} header on every protected request
 * and populates MDC with distributed tracing identifiers.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Populates {@code correlationId}, {@code requestId}, {@code method}, and {@code path}
 *       in the SLF4J MDC so every log line for the request carries these fields.</li>
 *   <li>For <em>protected paths</em>, validates that the {@code Authorization} header is present
 *       and uses the {@code Bearer} scheme, then passes the request to the next filter.</li>
 *   <li>Writes a structured {@link ErrorResponse} JSON body (same envelope as
 *       {@link com.example.user.api.ApiExceptionHandlerAdapter}) with HTTP 401 and the RFC 6750
 *       {@code WWW-Authenticate} header when validation fails.</li>
 * </ol>
 *
 * <h2>What it does NOT do</h2>
 * <p>This filter does <em>not</em> verify the JWT signature, claims, expiry, or audience.
 * Those concerns belong to Spring Security's {@code oauth2ResourceServer} JWT filter, which
 * runs after this one. This filter only checks header <em>presence</em> and <em>scheme</em>
 * so that the error response is a consistent JSON envelope rather than Spring Security's
 * default HTML/plain-text body.
 *
 * <h2>Active condition</h2>
 * <p>Only registered when {@link SecurityConfig.SecurityEnabledCondition} matches (i.e. JWT
 * issuer/JWK-set URI is configured or the {@code docker} profile is active). In plain H2/dev
 * mode the filter is absent and all requests pass through unimpeded.
 *
 * <h2>MDC keys</h2>
 * <ul>
 *   <li>{@code correlationId} — value of the incoming {@code X-Correlation-ID} header (omitted when absent)</li>
 *   <li>{@code requestId}     — value of the incoming {@code X-Request-ID} header, or a server-generated UUID</li>
 *   <li>{@code method}        — HTTP method</li>
 *   <li>{@code path}          — request URI</li>
 * </ul>
 * All MDC keys are removed in a {@code finally} block to prevent thread-local leaks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Conditional(SecurityConfig.SecurityEnabledCondition.class)
public class BearerTokenValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenValidationFilter.class);

    /**
     * Paths that are always allowed without an {@code Authorization} header.
     * Must stay in sync with the permit-all list in {@link SecurityConfig}.
     */
    static final List<String> PUBLIC_PATHS = List.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/health/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Maximum length for user-supplied header values stored in MDC (CWE-400). */
    public static final int MDC_MAX_VALUE_LENGTH = 128;

    /** Characters that must not appear in MDC values — prevents log injection (CWE-117). */
    private static final Pattern LOG_INJECTION_CHARS = Pattern.compile("[\\r\\n\\t]");

    private final ObjectMapper objectMapper;

    public BearerTokenValidationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path      = request.getRequestURI();
        String corrId    = sanitizeMdcValue(request.getHeader("X-Correlation-ID"));
        String requestId = sanitizeMdcValue(request.getHeader("X-Request-ID"));

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // ── Populate MDC ────────────────────────────────────────────────────────
        try {
            if (corrId != null && !corrId.isBlank()) {
                MDC.put("correlationId", corrId);
            }
            MDC.put("requestId", requestId);
            MDC.put("method", request.getMethod());
            MDC.put("path", path);

            // ── Skip public endpoints ────────────────────────────────────────────
            if (isPublicPath(path)) {
                chain.doFilter(request, response);
                return;
            }

            // ── Validate Authorization header ────────────────────────────────────
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("flow_stage=REQUEST_RECEIVED operation=auth.check status=MISSING_HEADER " +
                         "method={} path={} requestId={} correlationId={}",
                        request.getMethod(), path, requestId, corrId);
                writeError(response, request,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "MISSING_AUTHORIZATION_HEADER",
                        "Authorization header is required. " +
                        "Provide 'Authorization: Bearer <token>'.");
                return;
            }

            if (!authHeader.startsWith("Bearer ")) {
                log.warn("flow_stage=REQUEST_RECEIVED operation=auth.check status=INVALID_SCHEME " +
                         "method={} path={} requestId={} correlationId={}",
                        request.getMethod(), path, requestId, corrId);
                writeError(response, request,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID_AUTHORIZATION_HEADER",
                        "Authorization header must use the Bearer scheme: " +
                        "'Authorization: Bearer <token>'.");
                return;
            }

            String token = authHeader.substring("Bearer ".length());
            if (token.isBlank()) {
                log.warn("flow_stage=REQUEST_RECEIVED operation=auth.check status=EMPTY_TOKEN " +
                         "method={} path={} requestId={} correlationId={}",
                        request.getMethod(), path, requestId, corrId);
                writeError(response, request,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "EMPTY_BEARER_TOKEN",
                        "Bearer token must not be empty.");
                return;
            }

            log.debug("flow_stage=REQUEST_RECEIVED operation=auth.check status=HEADER_OK " +
                      "method={} path={} requestId={}",
                    request.getMethod(), path, requestId);

            chain.doFilter(request, response);

        } finally {
            // Always clean up MDC — thread pools reuse threads
            MDC.remove("correlationId");
            MDC.remove("requestId");
            MDC.remove("method");
            MDC.remove("path");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * Sanitizes a user-supplied header value before storing it in MDC.
     *
     * <ul>
     *   <li>Returns {@code null} for blank input.</li>
     *   <li>Replaces {@code CR}, {@code LF}, and {@code TAB} with {@code _} to prevent
     *       log-injection attacks (CWE-117 / OWASP A03).</li>
     *   <li>Truncates to {@link #MDC_MAX_VALUE_LENGTH} to prevent MDC bloat (CWE-400).</li>
     * </ul>
     */
    public static String sanitizeMdcValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String sanitized = LOG_INJECTION_CHARS.matcher(raw).replaceAll("_");
        return sanitized.length() > MDC_MAX_VALUE_LENGTH
                ? sanitized.substring(0, MDC_MAX_VALUE_LENGTH)
                : sanitized;
    }

    /**
     * Writes a JSON {@link ErrorResponse} directly to the servlet response.
     *
     * <p>The {@code WWW-Authenticate} header is included per
     * <a href="https://www.rfc-editor.org/rfc/rfc6750#section-3.1">RFC 6750 §3.1</a>.
     */
    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            int status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate",
                "Bearer realm=\"api\", error=\"invalid_token\", " +
                "error_description=\"" + message + "\"");

        ErrorResponse body = new ErrorResponse(code, message,
                request.getRequestURI(),
                Instant.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

