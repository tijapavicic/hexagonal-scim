package com.example.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the Spring Boot Actuator exposure is minimal and explicit.
 *
 * <p>Allowed endpoints (configured via {@code management.endpoints.web.exposure.include}):
 * <ul>
 *   <li>{@code /actuator/health}</li>
 *   <li>{@code /actuator/info}</li>
 *   <li>{@code /actuator/prometheus}</li>
 *   <li>{@code /actuator/metrics}</li>
 * </ul>
 *
 * <p>Sensitive endpoints that must NOT be reachable (must return {@code 404}):
 * <ul>
 *   <li>{@code /actuator/env} — exposes environment variables and system properties</li>
 *   <li>{@code /actuator/beans} — exposes the full Spring bean graph</li>
 *   <li>{@code /actuator/conditions} — exposes auto-configuration decisions</li>
 *   <li>{@code /actuator/configprops} — exposes resolved config property values</li>
 *   <li>{@code /actuator/loggers} — allows runtime log-level changes</li>
 *   <li>{@code /actuator/threaddump} — exposes JVM thread state</li>
 *   <li>{@code /actuator/heapdump} — downloads a full JVM heap dump</li>
 *   <li>{@code /actuator/shutdown} — would terminate the JVM remotely</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class ActuatorExposureTest {

    @Autowired
    private MockMvc mockMvc;

    // ─── Allowed endpoints must be reachable ──────────────────────────────────

    @Test
    @DisplayName("/actuator/health returns 200")
    void healthEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("/actuator/info returns 200")
    void infoEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/prometheus returns 200 with text/plain metrics")
    void prometheusEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/metrics returns 200")
    void metricsEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    // ─── Sensitive endpoints must NOT be reachable ────────────────────────────

    @ParameterizedTest(name = "/actuator/{0} must return 404 (not exposed)")
    @ValueSource(strings = {
            "env",
            "beans",
            "conditions",
            "configprops",
            "loggers",
            "threaddump",
            "heapdump"
    })
    @DisplayName("Sensitive read endpoints are not exposed")
    void sensitiveReadEndpointsAreNotExposed(String endpoint) throws Exception {
        mockMvc.perform(get("/actuator/" + endpoint))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/actuator/shutdown must return 404 (not exposed — would terminate the JVM)")
    void shutdownEndpointIsNotExposed() throws Exception {
        mockMvc.perform(post("/actuator/shutdown"))
                .andExpect(status().isNotFound());
    }

    // ─── Discovery endpoint lists only the approved subset ────────────────────

    @Test
    @DisplayName("GET /actuator root lists exactly: health, info, prometheus, metrics")
    void actuatorRootListsOnlyApprovedEndpoints() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.health").exists())
                .andExpect(jsonPath("$._links.info").exists())
                .andExpect(jsonPath("$._links.prometheus").exists())
                .andExpect(jsonPath("$._links.metrics").exists())
                // Sensitive endpoints must NOT be in the discovery response
                .andExpect(jsonPath("$._links.env").doesNotExist())
                .andExpect(jsonPath("$._links.beans").doesNotExist())
                .andExpect(jsonPath("$._links.shutdown").doesNotExist())
                .andExpect(jsonPath("$._links.heapdump").doesNotExist())
                .andExpect(jsonPath("$._links.threaddump").doesNotExist())
                .andExpect(jsonPath("$._links.configprops").doesNotExist())
                .andExpect(jsonPath("$._links.loggers").doesNotExist());
    }
}

