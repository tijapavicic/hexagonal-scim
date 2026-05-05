package com.example.user.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration for the hexagonal-scim REST API.
 * <p>
 * Swagger UI is available at: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a><br>
 * OpenAPI JSON spec is available at: <a href="http://localhost:8080/api-docs">http://localhost:8080/api-docs</a>
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hexagonalScimOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hexagonal SCIM API")
                        .description("""
                                Production-grade multi-module Spring Boot service using hexagonal architecture.
                                
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
                        new Server().url("http://app:8080").description("Docker Compose")
                ));
    }
}

