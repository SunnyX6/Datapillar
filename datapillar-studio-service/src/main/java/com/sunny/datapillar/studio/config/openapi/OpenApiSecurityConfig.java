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
 * OpenAPI安全配置
 * 负责为受保护接口补充鉴权声明与上下文来源说明
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiSecurityConfig {

    private static final String SECURITY_SCHEME = "GatewayBearerAuth";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String GATEWAY_CONTEXT_NOTE = """
            **网关上下文说明**
            - 当前接口的 `userId` / `tenantId` 由网关从 Bearer Token 解析并注入。
            - Header 区域展示的 `X-User-Id` / `X-Tenant-Id` 仅用于可视化说明上下文来源，不作为客户端入参。
            """;

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
        if (schemes != null && schemes.containsKey(SECURITY_SCHEME)) {
            return;
        }
        SecurityScheme scheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("对外请求通过网关鉴权，用户上下文由网关断言自动注入，无需且不能传 X-User-Id。");
        components.addSecuritySchemes(SECURITY_SCHEME, scheme);
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
            boolean existed = operation.getSecurity().stream()
                    .anyMatch(requirement -> requirement.containsKey(SECURITY_SCHEME));
            if (!existed) {
                operation.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
            }
            ensureGatewayContextHeaderParameters(operation);
            appendGatewayContextNote(operation);
        }
    }

    private void ensureGatewayContextHeaderParameters(Operation operation) {
        if (operation.getParameters() == null) {
            operation.setParameters(new ArrayList<>());
        }
        addHeaderParameterIfAbsent(
                operation,
                USER_ID_HEADER,
                "网关注入的用户ID（文档可视化字段，客户端无需也不应传递）");
        addHeaderParameterIfAbsent(
                operation,
                TENANT_ID_HEADER,
                "网关注入的租户ID（文档可视化字段，客户端无需也不应传递）");
    }

    private void addHeaderParameterIfAbsent(Operation operation, String headerName, String description) {
        boolean existed = operation.getParameters().stream()
                .anyMatch(parameter -> "header".equalsIgnoreCase(parameter.getIn())
                        && headerName.equalsIgnoreCase(parameter.getName()));
        if (existed) {
            return;
        }
        Parameter parameter = new Parameter()
                .name(headerName)
                .in("header")
                .required(false)
                .description(description)
                .schema(new StringSchema().example("由网关注入"));
        operation.addParametersItem(parameter);
    }

    private void appendGatewayContextNote(Operation operation) {
        String description = operation.getDescription();
        if (description == null || description.isBlank()) {
            operation.setDescription(GATEWAY_CONTEXT_NOTE);
            return;
        }
        if (description.contains("网关上下文说明")) {
            return;
        }
        operation.setDescription(description + "\n\n" + GATEWAY_CONTEXT_NOTE);
    }

    private boolean isProtectedPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/biz/") || path.startsWith("/admin/");
    }
}
