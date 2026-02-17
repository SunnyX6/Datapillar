package com.sunny.datapillar.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 认证OpenAPI配置
 * 负责认证OpenAPI网关访问地址声明
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiServerConfig {

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/api")));
    }
}

