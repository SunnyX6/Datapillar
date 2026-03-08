package com.sunny.datapillar.studio.config.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void shouldAddGovernanceTagGroupWhenGovernanceTagsExist() {
    OpenApiConfig config = new OpenApiConfig();
    OpenAPI openApi = new OpenAPI();
    openApi.setPaths(
        new Paths()
            .addPathItem(
                "/biz/metadata/catalogs",
                new PathItem().get(new Operation().addTagsItem("Metadata")))
            .addPathItem(
                "/admin/semantic/metrics",
                new PathItem().post(new Operation().addTagsItem("Semantic admin"))));

    config.apiResponseOpenApiCustomizer().customise(openApi);

    Object extension = openApi.getExtensions().get("x-tagGroups");
    List<?> tagGroups = assertInstanceOf(List.class, extension);
    Map<?, ?> governanceGroup =
        tagGroups.stream()
            .map(Map.class::cast)
            .filter(group -> "governance".equals(group.get("name")))
            .findFirst()
            .orElseThrow();

    assertEquals(List.of("Metadata", "Semantic admin"), governanceGroup.get("tags"));
  }

  @Test
  void shouldPreserveExistingModuleGroups() {
    OpenApiConfig config = new OpenApiConfig();
    OpenAPI openApi = new OpenAPI();
    openApi.setPaths(
        new Paths()
            .addPathItem(
                "/biz/projects", new PathItem().get(new Operation().addTagsItem("Project"))));

    config.apiResponseOpenApiCustomizer().customise(openApi);

    Object extension = openApi.getExtensions().get("x-tagGroups");
    List<?> tagGroups = assertInstanceOf(List.class, extension);

    assertTrue(
        tagGroups.stream()
            .map(Map.class::cast)
            .anyMatch(group -> "project".equals(group.get("name"))));
  }
}
