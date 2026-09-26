package com.parallax.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP Basic, stateless security (SPEC §9, §15). CSRF is disabled because this is a stateless API
 * authenticated per request with Basic. Unauthenticated and forbidden requests return an RFC 7807
 * ProblemDetail (401 / 403). Every URL rule lives here, in the order of SPEC §15; later prompts add
 * rules to this one class.
 */
@Configuration
@EnableConfigurationProperties(ParallaxUsersProperties.class)
public class SecurityConfig {

    /** The INTERNAL role set (SPEC §15). */
    private static final String[] INTERNAL = {"UNDERWRITER", "STRATEGIST", "APPROVER", "AUDITOR"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment,
                                           ObjectMapper objectMapper) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Liveness probe is public.
                    auth.requestMatchers("/actuator/health").permitAll();
                    // API docs and Swagger UI are exposed in the dev profile only.
                    if (dev) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    // GET /api/v1/me — any authenticated user.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated();
                    // POST /api/v1/applications — intake (SPEC §15).
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/applications")
                            .hasAnyRole("CLIENT", "UNDERWRITER", "STRATEGIST", "APPROVER");
                    // Read model (SPEC §15): INTERNAL everywhere; ASSISTANT only on the detail.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/applications").hasAnyRole(INTERNAL);
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/adverse-action-notice")
                            .hasAnyRole(INTERNAL);
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*")
                            .hasAnyRole("UNDERWRITER", "STRATEGIST", "APPROVER", "AUDITOR", "ASSISTANT");
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/decisions/*/reproduce").hasAnyRole(INTERNAL);
                    // Ledger reads + dev demos (SPEC §15) — INTERNAL.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/ledger", "/api/v1/ledger/stats",
                            "/api/v1/ledger/verify").hasAnyRole(INTERNAL);
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/ledger/demo/**").hasAnyRole(INTERNAL);
                    // Review queue + overrides (SPEC §15). The queue is INTERNAL; recording an override
                    // is UNDERWRITER only; override stats add ASSISTANT.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/reviews/queue").hasAnyRole(INTERNAL);
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/reviews/override-stats")
                            .hasAnyRole("UNDERWRITER", "STRATEGIST", "APPROVER", "AUDITOR", "ASSISTANT");
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/reviews/*").hasRole("UNDERWRITER");
                    // System screen (SPEC §15) — all INTERNAL; bureau-fault is dev only.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/system/status",
                            "/api/v1/system/idempotency-keys", "/api/v1/system/bureau-pulls").hasAnyRole(INTERNAL);
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/system/bureau-fault").hasAnyRole(INTERNAL);
                    // Everything else is denied until a later prompt adds its rule.
                    auth.anyRequest().denyAll();
                })
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint(objectMapper)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Loads the demo users (SPEC §9) with BCrypt-encoded passwords into an in-memory manager. */
    @Bean
    public UserDetailsService userDetailsService(ParallaxUsersProperties properties, PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        for (ParallaxUsersProperties.User u : properties.getUsers()) {
            String encoded = encoder.encode(resolvePassword(u.getPasswordEnv()));
            users.add(User.withUsername(u.getUsername())
                    .password(encoded)
                    .roles(u.getRole()) // granted as ROLE_<role>
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }

    /**
     * Password from the environment variable named by {@code passwordEnv} (default DEMO_PASSWORD),
     * falling back to the SPEC §9 dev default (demo-password, or assistant-dev for ASSISTANT_PASSWORD).
     */
    private String resolvePassword(String passwordEnv) {
        String envName = (passwordEnv == null || passwordEnv.isBlank()) ? "DEMO_PASSWORD" : passwordEnv;
        String value = System.getenv(envName);
        if (value != null) {
            return value;
        }
        return "ASSISTANT_PASSWORD".equals(envName) ? "assistant-dev" : "demo-password";
    }

    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeProblem(objectMapper, response,
                HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required");
    }

    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, deniedException) -> writeProblem(objectMapper, response,
                HttpStatus.FORBIDDEN, "Forbidden", "Access is denied");
    }

    private void writeProblem(ObjectMapper objectMapper, HttpServletResponse response,
                              HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
