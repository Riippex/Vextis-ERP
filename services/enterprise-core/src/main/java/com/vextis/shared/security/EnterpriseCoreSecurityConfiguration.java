package com.vextis.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
class EnterpriseCoreSecurityConfiguration {

    @Bean
    SecurityFilterChain enterpriseCoreSecurityFilterChain(
            HttpSecurity http,
            @Value("${vextis.exposure:INTERNAL}") String exposure,
            FirebaseTokenAuthenticationFilter firebaseFilter
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if ("PUBLIC".equalsIgnoreCase(exposure)) {
            http.addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            (request, response, cause) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                            .requestMatchers("/graphql").authenticated()
                            .requestMatchers("/internal/**").denyAll()
                            .anyRequest().denyAll());
        } else if ("LOCAL".equalsIgnoreCase(exposure)) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                    .requestMatchers("/internal/**").permitAll()
                    .anyRequest().denyAll());
        }

        return http.build();
    }
}
