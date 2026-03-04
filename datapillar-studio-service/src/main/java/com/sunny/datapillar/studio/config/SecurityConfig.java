package com.sunny.datapillar.studio.config;

import com.sunny.datapillar.studio.filter.SetupStateFilter;
import com.sunny.datapillar.studio.filter.TenantContextFilter;
import com.sunny.datapillar.studio.filter.TraceIdFilter;
import com.sunny.datapillar.studio.filter.TrustedIdentityFilter;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
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
 * Security configuration Responsible for safe configuration and assemblyBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(TrustedIdentityProperties.class)
public class SecurityConfig {

  private final TrustedIdentityFilter trustedIdentityFilter;
  private final SetupStateFilter setupStateFilter;
  private final TraceIdFilter traceIdFilter;
  private final TenantContextFilter tenantContextFilter;
  private final SecurityExceptionHandler securityExceptionHandler;

  public SecurityConfig(
      TrustedIdentityFilter trustedIdentityFilter,
      SetupStateFilter setupStateFilter,
      TraceIdFilter traceIdFilter,
      TenantContextFilter tenantContextFilter,
      SecurityExceptionHandler securityExceptionHandler) {
    this.trustedIdentityFilter = trustedIdentityFilter;
    this.setupStateFilter = setupStateFilter;
    this.traceIdFilter = traceIdFilter;
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
                auth
                    // Health Checks and Documentation Endpoints
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/setup/**")
                    .permitAll()
                    .requestMatchers("/biz/invitations/**")
                    .permitAll()
                    // All requests require authentication（Gateway Verified，trust here Gateway）
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(setupStateFilter, TraceIdFilter.class)
        .addFilterAfter(trustedIdentityFilter, SetupStateFilter.class)
        .addFilterAfter(tenantContextFilter, TrustedIdentityFilter.class);

    return http.build();
  }
}
