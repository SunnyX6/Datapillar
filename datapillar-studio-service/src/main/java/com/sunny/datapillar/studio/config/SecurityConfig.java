package com.sunny.datapillar.studio.config;

import com.sunny.datapillar.studio.filter.GatewayAssertionFilter;
import com.sunny.datapillar.studio.filter.SetupStateFilter;
import com.sunny.datapillar.studio.filter.TenantContextFilter;
import com.sunny.datapillar.studio.filter.TraceIdFilter;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.GatewayAssertionProperties;
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
 * 安全配置
 * 负责安全配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(GatewayAssertionProperties.class)
public class SecurityConfig {

    private final GatewayAssertionFilter gatewayAssertionFilter;
    private final SetupStateFilter setupStateFilter;
    private final TraceIdFilter traceIdFilter;
    private final TenantContextFilter tenantContextFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    public SecurityConfig(GatewayAssertionFilter gatewayAssertionFilter,
                          SetupStateFilter setupStateFilter,
                          TraceIdFilter traceIdFilter,
                          TenantContextFilter tenantContextFilter,
                          SecurityExceptionHandler securityExceptionHandler) {
        this.gatewayAssertionFilter = gatewayAssertionFilter;
        this.setupStateFilter = setupStateFilter;
        this.traceIdFilter = traceIdFilter;
        this.tenantContextFilter = tenantContextFilter;
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
                        // 健康检查与文档端点
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/setup/**").permitAll()
                        .requestMatchers("/biz/invitations/**").permitAll()
                        // 所有请求都需要认证（Gateway 已验证，这里信任 Gateway）
                        .anyRequest().authenticated()
                )
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(setupStateFilter, TraceIdFilter.class)
                .addFilterAfter(gatewayAssertionFilter, SetupStateFilter.class)
                .addFilterAfter(tenantContextFilter, GatewayAssertionFilter.class);

        return http.build();
    }
}
