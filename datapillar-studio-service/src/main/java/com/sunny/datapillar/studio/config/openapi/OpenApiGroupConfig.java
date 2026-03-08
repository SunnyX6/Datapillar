package com.sunny.datapillar.studio.config.openapi;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI group configuration for domain-focused documentation endpoints.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiGroupConfig {

  private static final String GOVERNANCE_GROUP = "governance";

  @Bean
  public GroupedOpenApi governanceOpenApi() {
    return GroupedOpenApi.builder()
        .group(GOVERNANCE_GROUP)
        .displayName("Governance API")
        .pathsToMatch(
            "/biz/metadata/**", "/admin/metadata/**", "/biz/semantic/**", "/admin/semantic/**")
        .build();
  }
}
