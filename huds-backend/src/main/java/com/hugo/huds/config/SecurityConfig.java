package com.hugo.huds.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // Disable the default HTML login page for the REST API.
                .formLogin(form -> form.disable())

                // Use HTTP Basic authentication during initial development.
                .httpBasic(Customizer.withDefaults())

                // Configure the API to operate without HTTP sessions.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Disable CSRF protection for the current stateless API setup.
                // Reassess this setting if browser-based cookie authentication is introduced.
                .csrf(csrf -> csrf.disable())

                // Define access rules for API endpoints.
                .authorizeHttpRequests(auth -> auth
                        // Allow anonymous access to health and application information endpoints.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Require authentication for all other endpoints.
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}