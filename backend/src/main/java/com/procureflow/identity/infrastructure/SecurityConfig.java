package com.procureflow.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.organization.infrastructure.web.TenantFilter;
import com.procureflow.shared.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security chain: no sessions, no CSRF (Bearer tokens are not
 * auto-attached by browsers), JSON problem responses instead of redirects.
 * Registration/login/refresh/logout plus docs and health stay public;
 * everything else needs a valid token, and fine-grained rules live on the
 * endpoints via {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"))
                        .accessDeniedHandler(jsonDeniedHandler(objectMapper)))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflights carry no credentials by spec; the actual
                        // request still authenticates and authorizes normally.
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new TenantFilter(), AuthorizationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint jsonEntryPoint(ObjectMapper mapper, HttpStatus status, String code) {
        return (request, response, ex) -> writeProblem(mapper, response, status, code);
    }

    private AccessDeniedHandler jsonDeniedHandler(ObjectMapper mapper) {
        return (request, response, ex) ->
                writeProblem(mapper, response, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private void writeProblem(
            ObjectMapper mapper, HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiError.of(code, status.getReasonPhrase()));
    }
}
