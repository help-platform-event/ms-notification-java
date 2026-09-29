package com.maxime.help.msnotification.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API trusts ms-auth's access tokens: HS256 JWTs signed with the shared {@code JWT_SECRET}
 * (Base64), exactly as the Gateway verifies them. This service never issues tokens.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        // Error pages (e.g. a 404 rendered by Spring Boot) must not turn into a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /** No default secret on purpose: without {@code JWT_SECRET}, the service refuses to start. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.jwt.secret:}") String secret) {
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set (the same Base64 value as ms-auth-java's)");
        }
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
