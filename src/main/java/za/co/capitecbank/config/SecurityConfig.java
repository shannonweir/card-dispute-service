package za.co.capitecbank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import za.co.capitecbank.security.SecurityErrorHandler;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity http, final SecurityErrorHandler errors) throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(
                                                "/openapi.yaml",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers("/api/v1/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(Customizer.withDefaults())
                                        .authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .build();
    }
}
