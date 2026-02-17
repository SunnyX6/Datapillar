package com.sunny.datapillar.studio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Studio OpenAPI配置
 * 负责Studio OpenAPI网关访问地址声明
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiServerConfig {

    @Bean
    public OpenAPI studioOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/api/studio")));
    }
}

