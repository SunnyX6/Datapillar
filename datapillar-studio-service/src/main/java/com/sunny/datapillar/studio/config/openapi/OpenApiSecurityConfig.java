package com.sunny.datapillar.studio.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPISecurity configuration Responsible for supplementing authentication statements and
 * contextual source descriptions for protected interfaces
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiSecurityConfig {

  private static final String JWT_SECURITY_SCHEME = "jwtBearerAuth";
  private static final String API_KEY_SECURITY_SCHEME = "apiKeyBearerAuth";
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String GATEWAY_CONTEXT_NOTE =
      """
**Gateway authentication**
- `/api/studio/**` uses `JWT`
- `/openapi/studio/**` uses `API_KEY`
- Both credentials are sent as `Authorization: Bearer <credential>`

**Gateway trusted headers**
- `X-User-Id` / `X-Tenant-Id` are injected by gateway after authentication
- These headers are shown only to explain downstream context and must not be sent by clients.""";

  @Bean
  public OpenApiCustomizer gatewaySecurityOpenApiCustomizer() {
    return openApi -> {
      ensureSecurityScheme(openApi);
      if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
        return;
      }
      openApi.getPaths().forEach(this::customizePathSecurity);
    };
  }

  private void ensureSecurityScheme(OpenAPI openApi) {
    Components components = openApi.getComponents();
    if (components == null) {
      components = new Components();
      openApi.setComponents(components);
    }
    Map<String, SecurityScheme> schemes = components.getSecuritySchemes();
    if (schemes == null || !schemes.containsKey(JWT_SECURITY_SCHEME)) {
      components.addSecuritySchemes(
          JWT_SECURITY_SCHEME,
          new SecurityScheme()
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("JWT")
              .description("Gateway App API authentication. Use on `/api/studio/**` routes."));
    }
    if (schemes == null || !schemes.containsKey(API_KEY_SECURITY_SCHEME)) {
      components.addSecuritySchemes(
          API_KEY_SECURITY_SCHEME,
          new SecurityScheme()
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("API_KEY")
              .description("Gateway Open API authentication. Use on `/openapi/studio/**` routes."));
    }
  }

  private void customizePathSecurity(String path, PathItem pathItem) {
    if (!isProtectedPath(path) || pathItem == null) {
      return;
    }
    for (Operation operation : pathItem.readOperations()) {
      if (operation == null) {
        continue;
      }
      if (operation.getSecurity() == null) {
        operation.setSecurity(new ArrayList<>());
      }
      boolean existed =
          operation.getSecurity().stream()
              .anyMatch(
                  requirement ->
                      requirement.containsKey(JWT_SECURITY_SCHEME)
                          || requirement.containsKey(API_KEY_SECURITY_SCHEME));
      if (!existed) {
        operation.addSecurityItem(new SecurityRequirement().addList(JWT_SECURITY_SCHEME));
        operation.addSecurityItem(new SecurityRequirement().addList(API_KEY_SECURITY_SCHEME));
      }
      ensureGatewayHeaderParams(operation);
      appendGatewayContextNote(operation);
    }
  }

  private void ensureGatewayHeaderParams(Operation operation) {
    if (operation.getParameters() == null) {
      operation.setParameters(new ArrayList<>());
    }
    addHeaderParameterIfAbsent(
        operation,
        USER_ID_HEADER,
        "Gateway injected userID(Document visualization fields,The client does not need to and"
            + " should not pass)");
    addHeaderParameterIfAbsent(
        operation,
        TENANT_ID_HEADER,
        "Gateway-injected tenantID(Document visualization fields,The client does not need to and"
            + " should not pass)");
  }

  private void addHeaderParameterIfAbsent(
      Operation operation, String headerName, String description) {
    boolean existed =
        operation.getParameters().stream()
            .anyMatch(
                parameter ->
                    "header".equalsIgnoreCase(parameter.getIn())
                        && headerName.equalsIgnoreCase(parameter.getName()));
    if (existed) {
      return;
    }
    Parameter parameter =
        new Parameter()
            .name(headerName)
            .in("header")
            .required(false)
            .description(description)
            .schema(new StringSchema().example("Injected by gateway"));
    operation.addParametersItem(parameter);
  }

  private void appendGatewayContextNote(Operation operation) {
    String description = operation.getDescription();
    if (description == null || description.isBlank()) {
      operation.setDescription(GATEWAY_CONTEXT_NOTE);
      return;
    }
    if (description.contains("Gateway context description")) {
      return;
    }
    operation.setDescription(description + "\n\n" + GATEWAY_CONTEXT_NOTE);
  }

  private boolean isProtectedPath(String path) {
    if (path == null) {
      return false;
    }
    if (path.startsWith("/biz/invitations")) {
      return false;
    }
    return path.startsWith("/biz/") || path.startsWith("/admin/");
  }
}
