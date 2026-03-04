package com.sunny.datapillar.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI server config for auth gateway endpoint declaration.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiServerConfig {

  @Bean
  public OpenAPI authOpenApi() {
    return new OpenAPI().servers(List.of(new Server().url("/api")));
  }
}
