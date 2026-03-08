package com.sunny.datapillar.studio.config.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

class OpenApiGroupConfigTest {

  @Test
  void shouldRegisterGovernanceGroupedOpenApi() {
    OpenApiGroupConfig config = new OpenApiGroupConfig();

    GroupedOpenApi groupedOpenApi = config.governanceOpenApi();

    assertEquals("governance", groupedOpenApi.getGroup());
    assertEquals("Governance API", groupedOpenApi.getDisplayName());
    assertIterableEquals(
        List.of("/biz/metadata/**", "/admin/metadata/**", "/biz/semantic/**", "/admin/semantic/**"),
        groupedOpenApi.getPathsToMatch());
  }
}
