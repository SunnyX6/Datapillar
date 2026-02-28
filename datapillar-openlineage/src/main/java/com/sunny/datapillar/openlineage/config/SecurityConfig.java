package com.sunny.datapillar.openlineage.config;

import com.sunny.datapillar.openlineage.handler.SecurityExceptionHandler;
import com.sunny.datapillar.openlineage.security.GatewayAssertionProperties;
import com.sunny.datapillar.openlineage.security.OpenLineageAuthFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(GatewayAssertionProperties.class)
public class SecurityConfig {

    private final OpenLineageAuthFilter openLineageAuthFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    public SecurityConfig(OpenLineageAuthFilter openLineageAuthFilter,
                          SecurityExceptionHandler securityExceptionHandler) {
        this.openLineageAuthFilter = openLineageAuthFilter;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(openLineageAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
