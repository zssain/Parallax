package com.parallax.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.List;

/**
 * HTTP Basic, stateless security (SPEC §9, §15). Reads are INTERNAL (UNDERWRITER, STRATEGIST, APPROVER,
 * AUDITOR); writes (CLI requests, collection actions, the dev simulate-month) are UNDERWRITER only.
 * {@code /internal/v1/**} bypasses Basic auth — the InternalTokenFilter enforces the shared token —
 * and the actuator health probe is public. The four INTERNAL demo users match the other services.
 */
@Configuration
public class AccountSecurityConfig {

    private static final String[] INTERNAL = {"UNDERWRITER", "STRATEGIST", "APPROVER", "AUDITOR"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment,
                                           ObjectMapper objectMapper) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health").permitAll();
                    // Service-to-service endpoints: gated by the token filter, not Basic auth.
                    auth.requestMatchers("/internal/v1/**").permitAll();
                    if (dev) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    // Reads (SPEC §15) — INTERNAL.
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/accounts", "/api/v1/accounts/*",
                            "/api/v1/collections", "/api/v1/collections/summary").hasAnyRole(INTERNAL);
                    // Writes — UNDERWRITER only.
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/cli-requests").hasRole("UNDERWRITER");
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/simulate-month").hasRole("UNDERWRITER");
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/collections/*/actions").hasRole("UNDERWRITER");
                    auth.anyRequest().denyAll();
                })
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint(objectMapper)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The four INTERNAL demo users (SPEC §9), same emails and password as the other services. */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String password = encoder.encode(demoPassword());
        List<UserDetails> users = List.of(
                user("aditi.rao@parallax.dev", password, "STRATEGIST"),
                user("vikram.nair@parallax.dev", password, "APPROVER"),
                user("priya.menon@parallax.dev", password, "UNDERWRITER"),
                user("sam.iyer@parallax.dev", password, "AUDITOR"));
        return new InMemoryUserDetailsManager(users);
    }

    private static String demoPassword() {
        String env = System.getenv("DEMO_PASSWORD");
        return env != null ? env : "demo-password";
    }

    private static UserDetails user(String username, String encodedPassword, String role) {
        return User.withUsername(username).password(encodedPassword).roles(role).build();
    }

    private AuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) ->
                writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Authentication is required");
    }

    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, deniedException) ->
                writeProblem(objectMapper, response, HttpStatus.FORBIDDEN, "Forbidden", "Access is denied");
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
