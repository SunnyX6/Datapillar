package com.sunny.datapillar.studio.config;

import com.sunny.datapillar.studio.filter.IdentityStateValidationFilter;
import com.sunny.datapillar.studio.filter.SetupStateFilter;
import com.sunny.datapillar.studio.filter.TenantContextFilter;
import com.sunny.datapillar.studio.filter.TraceIdFilter;
import com.sunny.datapillar.studio.filter.TrustedIdentityAuthenticationFilter;
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

  private final TrustedIdentityAuthenticationFilter trustedIdentityAuthenticationFilter;
  private final SetupStateFilter setupStateFilter;
  private final TraceIdFilter traceIdFilter;
  private final TenantContextFilter tenantContextFilter;
  private final IdentityStateValidationFilter identityStateValidationFilter;
  private final SecurityExceptionHandler securityExceptionHandler;

  public SecurityConfig(
      TrustedIdentityAuthenticationFilter trustedIdentityAuthenticationFilter,
      SetupStateFilter setupStateFilter,
      TraceIdFilter traceIdFilter,
      TenantContextFilter tenantContextFilter,
      IdentityStateValidationFilter identityStateValidationFilter,
      SecurityExceptionHandler securityExceptionHandler) {
    this.trustedIdentityAuthenticationFilter = trustedIdentityAuthenticationFilter;
    this.setupStateFilter = setupStateFilter;
    this.traceIdFilter = traceIdFilter;
    this.tenantContextFilter = tenantContextFilter;
    this.identityStateValidationFilter = identityStateValidationFilter;
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
        .addFilterAfter(trustedIdentityAuthenticationFilter, SetupStateFilter.class)
        .addFilterAfter(tenantContextFilter, TrustedIdentityAuthenticationFilter.class)
        .addFilterAfter(identityStateValidationFilter, TenantContextFilter.class);

    return http.build();
  }
}
