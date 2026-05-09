package com.example.user.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p>Active only when {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * is set in the environment — which happens automatically when the application is
 * started via Docker Compose (the variable is pre-wired in {@code docker-compose.yml}).
 *
 * <p>When the property is absent (plain {@code mvn spring-boot:run} with H2, no Keycloak),
 * this configuration is skipped, keeping local development friction-free.
 *
 * <h2>Public endpoints</h2>
 * <ul>
 *   <li>{@code /actuator/health} — liveness probe used by Docker Compose healthcheck</li>
 *   <li>{@code /swagger-ui/**}, {@code /api-docs/**} — API documentation</li>
 * </ul>
 *
 * <h2>Role extraction</h2>
 * Keycloak embeds realm roles in the JWT under {@code realm_access.roles}.
 * The converter maps each role to a Spring Security {@code ROLE_<UPPERCASE>} authority,
 * enabling {@code @PreAuthorize("hasRole('admin')")} annotations on controller methods.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
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

