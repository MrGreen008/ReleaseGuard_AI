package dev.releaseguard;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
class LocalSecurity {
    @Bean
    InMemoryUserDetailsManager users(Environment environment,
            @Value("${release.auth.viewer}") String viewer,
            @Value("${release.auth.approver}") String approver,
            @Value("${release.auth.shop}") String shop) {
        var users = new InMemoryUserDetailsManager();
        var encoder = new BCryptPasswordEncoder();
        if (environment.acceptsProfiles(Profiles.of("shop"))) {
            users.createUser(User.withUsername("simulator").password(encode(encoder, shop)).roles("SHOP").build());
        } else {
            if (viewer.equals(approver)) {
                throw new IllegalArgumentException("Viewer and approver passwords must differ.");
            }
            users.createUser(User.withUsername("viewer").password(encode(encoder, viewer)).roles("VIEWER").build());
            users.createUser(User.withUsername("approver").password(encode(encoder, approver)).roles("APPROVER").build());
        }
        return users;
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, Environment environment) throws Exception {
        boolean shop = environment.acceptsProfiles(Profiles.of("shop"));
        return http
            .csrf(config -> config.disable())
            .requestCache(config -> config.disable())
            .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore((request, response, chain) -> {
                var incoming = (jakarta.servlet.http.HttpServletRequest) request;
                if (List.of("POST", "PUT", "PATCH", "DELETE").contains(incoming.getMethod())
                    && (!"1".equals(incoming.getHeader("X-ReleaseGuard-Intent"))
                    || "cross-site".equals(incoming.getHeader("Sec-Fetch-Site")))) {
                    ((jakarta.servlet.http.HttpServletResponse) response)
                        .sendError(HttpStatus.FORBIDDEN.value(), "Explicit operation header required.");
                    return;
                }
                chain.doFilter(request, response);
            }, CsrfFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health").permitAll();
                if (shop) {
                    auth.requestMatchers("/api/**").hasRole("SHOP");
                } else {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/**").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .hasAnyRole("VIEWER", "APPROVER");
                }
                auth.anyRequest().denyAll();
            }).build();
    }

    static String authHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String encode(BCryptPasswordEncoder encoder, String value) {
        if (value.length() < 16 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Local credentials must contain 16 to 72 bytes.");
        }
        return encoder.encode(value);
    }
}
