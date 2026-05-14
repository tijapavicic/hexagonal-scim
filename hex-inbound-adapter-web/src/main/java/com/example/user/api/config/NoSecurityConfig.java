package com.example.user.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.lang.NonNull;

import java.util.Arrays;

/**
 * Fallback security configuration for local unsecured mode.
 *
 * <p>It activates only when no JWT resource-server properties are configured
 * and the {@code docker} profile is not active.
 *
 * <p>When running via Docker Compose with Keycloak, {@link SecurityConfig} is loaded
 * and this open filter chain is skipped.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional(NoSecurityConfig.OpenSecurityCondition.class)
public class NoSecurityConfig {

    static class OpenSecurityCondition implements Condition {
        @Override
        public boolean matches(@NonNull ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean hasIssuer = hasText(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                    || hasText(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer.uri"));
            boolean hasJwkSet = hasText(environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                    || hasText(environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk.set.uri"));
            boolean dockerProfileActive = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch("docker"::equalsIgnoreCase);
            return !(hasIssuer || hasJwkSet || dockerProfileActive);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    @Bean
    public SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
