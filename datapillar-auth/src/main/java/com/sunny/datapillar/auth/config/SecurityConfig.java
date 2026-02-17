package com.sunny.datapillar.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.sunny.datapillar.auth.filter.TraceIdFilter;
import com.sunny.datapillar.auth.handler.SecurityExceptionHandler;
import com.sunny.datapillar.common.utils.JwtUtil;
/**
 * 安全配置
 * 负责安全配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class SecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final AuthSecurityProperties securityProperties;

    public SecurityConfig(TraceIdFilter traceIdFilter,
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
                argon2.getIterations()
        );
    }

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.issuer}") String issuer) {
        return new JwtUtil(secret, issuer);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 Spring Security 内置 CSRF，使用自定义 CSRF 校验
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用 Session (认证服务是无状态的)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                // 配置路径权限
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // 所有认证相关接口都允许访问
                        .requestMatchers("/auth/**", "/login/**").permitAll()
                        // 其他请求拒绝访问
                        .anyRequest().denyAll()
                )
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
