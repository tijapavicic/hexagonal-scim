package com.example.user.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth2 JWT resource server security configuration.
 *
 * <p>Active when <em>either</em>:
 * <ul>
 *   <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set, <em>or</em></li>
 *   <li>{@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} is set.</li>
 * </ul>
 *
 * <p>Using {@code jwk-set-uri} (without {@code issuer-uri}) in Docker Compose solves the
 * split-network issuer-mismatch problem: the browser obtains tokens from
 * {@code http://localhost:8180} (so {@code iss} = {@code http://localhost:8180/realms/…}),
 * while the Spring app fetches JWKS from the Docker-internal host
 * {@code http://keycloak:8180/…}.  Configuring only {@code jwk-set-uri} performs signature
 * validation without an issuer-equality check, which is correct for a local dev environment.
 *
 * <p>When neither property is set (plain {@code mvn spring-boot:run} with H2),
 * this configuration is skipped and {@link NoSecurityConfig} provides a permit-all fallback.
 *
 * <h2>Public endpoints (no token required)</h2>
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info}</li>
 *   <li>{@code /swagger-ui/**}, {@code /api-docs/**}</li>
 * </ul>
 *
 * <h2>Role extraction</h2>
 * Keycloak embeds realm roles under {@code realm_access.roles} in the JWT.
 * Each role is mapped to {@code ROLE_<UPPERCASE>}, enabling
 * {@code @PreAuthorize("hasRole('admin')")} on controller methods.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnExpression(
        "!'${spring.security.oauth2.resourceserver.jwt.issuer-uri:}'.isEmpty()" +
        " || !'${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}'.isEmpty()"
)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is intentionally disabled at the Spring layer.
                // In production, the React SPA is served by the same Nginx instance that
                // reverse-proxies /api/* to this service — so the browser never sees a
                // cross-origin request.  Keeping CORS disabled here prevents accidental
                // permissive header leaks if the app is ever run outside of Nginx.
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                );
        return http.build();
    }

    /**
     * Converts Keycloak JWT {@code realm_access.roles} into Spring Security authorities.
     *
     * <p>Example JWT claim:
     * <pre>
     * "realm_access": { "roles": ["user", "admin"] }
     * </pre>
     * Becomes: {@code ROLE_USER}, {@code ROLE_ADMIN}
     */
    private JwtAuthenticationConverter keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}
