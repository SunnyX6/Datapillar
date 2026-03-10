package com.sunny.datapillar.studio.config.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiSecurityConfigTest {

  @Test
  void shouldRegisterGatewaySecuritySchemesAndProtectedOperationSecurity() {
    OpenApiSecurityConfig config = new OpenApiSecurityConfig();
    OpenAPI openApi = new OpenAPI();
    openApi.setPaths(
        new Paths()
            .addPathItem("/biz/projects", new PathItem().get(new Operation()))
            .addPathItem("/biz/invitations/accept", new PathItem().post(new Operation())));

    config.gatewaySecurityOpenApiCustomizer().customise(openApi);

    SecurityScheme jwtScheme = openApi.getComponents().getSecuritySchemes().get("jwtBearerAuth");
    SecurityScheme apiKeyScheme =
        openApi.getComponents().getSecuritySchemes().get("apiKeyBearerAuth");
    assertNotNull(jwtScheme);
    assertNotNull(apiKeyScheme);
    assertEquals("JWT", jwtScheme.getBearerFormat());
    assertEquals("API_KEY", apiKeyScheme.getBearerFormat());

    Operation protectedOperation = openApi.getPaths().get("/biz/projects").getGet();
    assertNotNull(protectedOperation.getSecurity());
    assertEquals(2, protectedOperation.getSecurity().size());
    assertTrue(protectedOperation.getSecurity().get(0).containsKey("jwtBearerAuth"));
    assertTrue(protectedOperation.getSecurity().get(1).containsKey("apiKeyBearerAuth"));

    Operation publicOperation = openApi.getPaths().get("/biz/invitations/accept").getPost();
    assertTrue(publicOperation.getSecurity() == null || publicOperation.getSecurity().isEmpty());
  }
}
