package com.linkpocket.auth.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:local-google-client-id}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret
    ) {
        ClientRegistration.Builder google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .scope("email", "profile")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
        if (!clientSecret.isBlank()) {
            google.clientSecret(clientSecret);
        }
        return new InMemoryClientRegistrationRepository(google.build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/extension/**").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        .requestMatchers("/api/logout").permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor(
                                apiAuthenticationEntryPoint,
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .oauth2Login(Customizer.withDefaults());
        return http.build();
    }
}
