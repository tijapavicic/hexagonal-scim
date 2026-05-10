package com.example.user.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fallback security configuration — active only when no {@link SecurityFilterChain} bean
 * has been registered by another configuration class.
 *
 * <p>In practice this fires when {@link SecurityConfig} is skipped because
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is not set — i.e.:
 * <ul>
 *   <li>Local development with H2 ({@code mvn spring-boot:run}, no Docker)</li>
 *   <li>Integration tests ({@code @SpringBootTest} without Keycloak env var)</li>
 * </ul>
 *
 * <p>This class ensures Spring Boot's own opinionated default security (HTTP Basic with
 * random password) does not activate and produce unexpected {@code 401} responses in
 * these environments. All requests are permitted; CSRF is disabled to keep REST semantics.
 *
 * <p>When running via Docker Compose with Keycloak, {@link SecurityConfig} is loaded
 * (the {@code issuer-uri} property is set), registers a {@link SecurityFilterChain} bean,
 * and this class becomes a no-op via {@link ConditionalOnMissingBean}.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class NoSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

