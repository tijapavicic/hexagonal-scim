package com.example.user.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration for the hexagonal-scim REST API.
 * <p>
 * Swagger UI is available at: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a><br>
 * OpenAPI JSON spec is available at: <a href="http://localhost:8080/api-docs">http://localhost:8080/api-docs</a>
 *
 * <p>When Keycloak is running (Docker Compose), clicking <b>Authorize</b> in Swagger UI
 * and using the {@code password} flow with {@code hexagonal-scim-public} client lets you
 * test secured endpoints directly from the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME   = "bearerAuth";
    private static final String KEYCLOAK_SCHEME = "keycloak";

    /**
     * Keycloak token endpoint — defaults to local Docker Compose URL.
     * Override via {@code SPRINGDOC_SWAGGER_UI_OAUTH_TOKEN_URL} environment variable.
     */
    @Value("${springdoc.swagger-ui.oauth.token-url:" +
           "http://localhost:8180/realms/hexagonal-scim/protocol/openid-connect/token}")
    private String keycloakTokenUrl;

    @Bean
    public OpenAPI hexagonalScimOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hexagonal SCIM API")
                        .description("""
                                Production-grade multi-module Spring Boot service using hexagonal architecture.

                                **Authentication** (when running via Docker Compose):
                                - Click **Authorize** → use the `keycloak` (Password flow) or paste a Bearer token
                                - Token endpoint: `http://localhost:8180/realms/hexagonal-scim/protocol/openid-connect/token`
                                - Client ID: `hexagonal-scim-public`  ·  Test user: `testuser` / `password`

                                **Versioning:**
                                - `/api/v1/users` — current stable version
                                - `/api/users` — legacy path (deprecated, returns `Deprecation`, `Sunset`, `Link` response headers)

                                **Pagination:**
                                - `GET /api/v1/users` — first page of results (`page=0`, `size=10` by default, both configurable)
                                - Add `?pageable=false` to return every user in a single response (no pagination)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("hexagonal-scim")
                                .url("https://github.com/example/hexagonal-scim"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("http://app:8080").description("Docker Compose")))
                // Global security requirement — every endpoint requires one of these schemes
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK_SCHEME))
                .components(new Components()
                        // Scheme 1: paste a raw Bearer token (works without Keycloak running)
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a JWT obtained from Keycloak or another OIDC provider."))
                        // Scheme 2: interactive password flow directly against Keycloak
                        .addSecuritySchemes(KEYCLOAK_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Log in with Keycloak using the password flow. " +
                                             "Client ID: hexagonal-scim-public · User: testuser / password")
                                .flows(new OAuthFlows()
                                        .password(new OAuthFlow()
                                                .tokenUrl(keycloakTokenUrl)
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect")
                                                        .addString("profile", "User profile")
                                                        .addString("email", "User email"))))));
    }
}

