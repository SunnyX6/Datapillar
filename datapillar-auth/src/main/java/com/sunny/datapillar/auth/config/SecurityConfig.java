package com.sunny.datapillar.auth.config;

import com.sunny.datapillar.auth.filter.TraceIdFilter;
import com.sunny.datapillar.auth.handler.SecurityExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for bean wiring and request authorization setup.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthSecurityProperties.class, AuthProperties.class})
public class SecurityConfig {

  private final TraceIdFilter traceIdFilter;
  private final SecurityExceptionHandler securityExceptionHandler;
  private final AuthSecurityProperties securityProperties;

  public SecurityConfig(
      TraceIdFilter traceIdFilter,
      SecurityExceptionHandler securityExceptionHandler,
      AuthSecurityProperties securityProperties) {
    this.traceIdFilter = traceIdFilter;
    this.securityExceptionHandler = securityExceptionHandler;
    this.securityProperties = securityProperties;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    AuthSecurityProperties.Password.Argon2 argon2 = securityProperties.getPassword().getArgon2();
    int memoryKb = Math.max(1, argon2.getMemoryMb()) * 1024;
    return new Argon2PasswordEncoder(
        argon2.getSaltLength(),
        argon2.getHashLength(),
        argon2.getParallelism(),
        memoryKb,
        argon2.getIterations());
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Disable Spring Security built-in CSRF and use custom CSRF validation.
        .csrf(AbstractHttpConfigurer::disable)
        // Disable session state because auth service is stateless.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler))
        // Configure route authorization.
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    // Allow all authentication-related endpoints.
                    .requestMatchers("/.well-known/**", "/oauth2/**", "/auth/**")
                    .permitAll()
                    // Deny any other requests.
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
