package com.sunny.datapillar.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security configuration Responsible for gateway security configuration, assembly
 * andBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({GatewaySecurityProperties.class, AuthenticationProperties.class})
public class GatewaySecurityConfig {

  /**
   * Disable Spring Security default challenge pipeline in gateway.
   *
   * <p>Gateway authorization is enforced by custom global filters, not by Spring Security endpoint
   * guards.
   */
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .build();
  }
}
