package com.sunny.datapillar.studio.config;

import com.sunny.datapillar.studio.security.GatewayUserAuthenticationFilter;
import com.sunny.datapillar.studio.security.TenantContextFilter;
import com.sunny.datapillar.studio.security.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 配置
 * Gateway 已完成 JWT 认证，此处仅处理权限校验
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final GatewayUserAuthenticationFilter gatewayUserAuthenticationFilter;
    private final TraceIdFilter traceIdFilter;
    private final TenantContextFilter tenantContextFilter;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(GatewayUserAuthenticationFilter gatewayUserAuthenticationFilter,
                          TraceIdFilter traceIdFilter,
                          TenantContextFilter tenantContextFilter,
                          SecurityExceptionHandler securityExceptionHandler,
                          CorsConfigurationSource corsConfigurationSource) {
        this.gatewayUserAuthenticationFilter = gatewayUserAuthenticationFilter;
        this.traceIdFilter = traceIdFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.securityExceptionHandler = securityExceptionHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        // 健康检查等公开端点
                        .requestMatchers("/health", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // 所有请求都需要认证（Gateway 已验证，这里信任 Gateway）
                        .anyRequest().authenticated()
                )
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, TraceIdFilter.class)
                .addFilterAfter(gatewayUserAuthenticationFilter, TenantContextFilter.class);

        return http.build();
    }
}
