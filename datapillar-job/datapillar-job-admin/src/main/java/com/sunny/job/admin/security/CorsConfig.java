package com.sunny.job.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 *
 * 允许前端跨域访问 Datapillar-Job Admin API
 *
 * @author sunny
 * @since 2025-12-08
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的源（前端地址）
        config.addAllowedOriginPattern("*");  // 允许所有源，生产环境应改为具体域名

        // 允许的请求方法
        config.addAllowedMethod("*");  // GET, POST, PUT, DELETE, OPTIONS 等

        // 允许的请求头
        config.addAllowedHeader("*");

        // 允许携带认证信息（cookies）
        config.setAllowCredentials(true);

        // 暴露的响应头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Disposition");

        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
