package com.sunny.datapillar.openlineage.config;

import com.sunny.datapillar.openlineage.web.filter.TenantContextFilter;
import com.sunny.datapillar.openlineage.web.filter.TrustedIdentityAuthenticationFilter;
import com.sunny.datapillar.openlineage.web.filter.TrustedIdentityProperties;
import com.sunny.datapillar.openlineage.web.handler.SecurityExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Security configuration. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(TrustedIdentityProperties.class)
public class SecurityConfig {

  private final TrustedIdentityAuthenticationFilter trustedIdentityAuthenticationFilter;
  private final TenantContextFilter tenantContextFilter;
  private final SecurityExceptionHandler securityExceptionHandler;

  public SecurityConfig(
      TrustedIdentityAuthenticationFilter trustedIdentityAuthenticationFilter,
      TenantContextFilter tenantContextFilter,
      SecurityExceptionHandler securityExceptionHandler) {
    this.trustedIdentityAuthenticationFilter = trustedIdentityAuthenticationFilter;
    this.tenantContextFilter = tenantContextFilter;
    this.securityExceptionHandler = securityExceptionHandler;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/metrics/**",
                        "/actuator/prometheus",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            trustedIdentityAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(tenantContextFilter, TrustedIdentityAuthenticationFilter.class);
    return http.build();
  }
}
