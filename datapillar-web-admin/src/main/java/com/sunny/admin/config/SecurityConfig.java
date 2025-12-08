package com.sunny.admin.config;

import com.sunny.admin.security.GatewayUserAuthenticationFilter;
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
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(GatewayUserAuthenticationFilter gatewayUserAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.gatewayUserAuthenticationFilter = gatewayUserAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 健康检查等公开端点
                        .requestMatchers("/health", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // 所有请求都需要认证（Gateway 已验证，这里信任 Gateway）
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewayUserAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
