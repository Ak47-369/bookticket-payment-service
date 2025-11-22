package com.bookticket.payment_service.configuration;

import com.bookticket.payment_service.security.HeaderAuthenticatorFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticatorFilter headerAuthenticatorFilter() {
        return new HeaderAuthenticatorFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/internal/payments/**").hasRole("SERVICE_ACCOUNT")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticatorFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
