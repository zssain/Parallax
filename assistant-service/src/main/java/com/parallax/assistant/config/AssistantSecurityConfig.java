package com.parallax.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;
import java.util.List;

/**
 * HTTP Basic, stateless security (SPEC §9, §12). The four INTERNAL demo users may reach the assistant;
 * everything under {@code /api/v1/assistant/**} and the MCP transport endpoints ({@code /sse},
 * {@code /mcp/message}) requires an INTERNAL role, the actuator health probe is public, and everything
 * else is denied. Unauthenticated requests get a 401 ProblemDetail.
 */
@Configuration
public class AssistantSecurityConfig {

    private static final String[] INTERNAL = {"UNDERWRITER", "STRATEGIST", "APPROVER", "AUDITOR"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health").permitAll();
                    auth.requestMatchers("/api/v1/assistant/**").hasAnyRole(INTERNAL);
                    // MCP server transport (SSE + message endpoints): same INTERNAL HTTP Basic as chat,
                    // so MCP clients authenticate as an INTERNAL user and the tools are read-only. These
                    // are functional RouterFunction endpoints, so match them by explicit path pattern
                    // (the default MvcRequestMatcher only resolves @RequestMapping handlers).
                    auth.requestMatchers(
                            PathPatternRequestMatcher.withDefaults().matcher("/sse"),
                            PathPatternRequestMatcher.withDefaults().matcher("/mcp/message")).hasAnyRole(INTERNAL);
                    auth.anyRequest().denyAll();
                })
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint(objectMapper)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint(objectMapper)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The four INTERNAL demo users (SPEC §9), same emails and password as application-service. */
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
        return (request, response, authException) -> writeProblem(objectMapper, response);
    }

    private void writeProblem(ObjectMapper objectMapper, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required");
        problem.setTitle("Unauthorized");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
