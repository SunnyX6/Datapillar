package com.sunny.datapillar.auth.config;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证OpenAPI响应模型配置
 * 负责裁剪认证接口文档中的分页字段
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiConfig {

    private static final Set<String> PAGE_FIELDS = Set.of("limit", "offset", "total");
    private static final String DATA_FIELD = "data";

    @Bean
    public OpenApiCustomizer authApiResponseOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }
                        for (ApiResponse response : operation.getResponses().values()) {
                            if (response == null || response.getContent() == null) {
                                continue;
                            }
                            for (MediaType mediaType : response.getContent().values()) {
                                if (mediaType == null || mediaType.getSchema() == null) {
                                    continue;
                                }
                                Schema<?> targetSchema = resolveSchema(openApi, mediaType.getSchema());
                                if (targetSchema == null) {
                                    continue;
                                }
                                Schema<?> adjustedSchema = targetSchema;
                                if (containsPageField(adjustedSchema)) {
                                    adjustedSchema = removePageFields(adjustedSchema);
                                }
                                if (containsVoidDataField(adjustedSchema)) {
                                    adjustedSchema = removeDataField(adjustedSchema);
                                }
                                if (adjustedSchema != targetSchema) {
                                    mediaType.setSchema(adjustedSchema);
                                }
                            }
                        }
                    }));
        };
    }

    @SuppressWarnings("unchecked")
    private Schema<?> removePageFields(Schema<?> schema) {
        Schema<?> copied = Json.mapper().convertValue(schema, Schema.class);
        Map<String, Schema> properties = copied.getProperties();
        if (properties != null) {
            PAGE_FIELDS.forEach(properties::remove);
        }
        List<String> required = copied.getRequired();
        if (required != null) {
            required.removeIf(PAGE_FIELDS::contains);
        }
        return copied;
    }

    @SuppressWarnings("unchecked")
    private Schema<?> removeDataField(Schema<?> schema) {
        Schema<?> copied = Json.mapper().convertValue(schema, Schema.class);
        Map<String, Schema> properties = copied.getProperties();
        if (properties != null) {
            properties.remove(DATA_FIELD);
        }
        List<String> required = copied.getRequired();
        if (required != null) {
            required.remove(DATA_FIELD);
        }
        return copied;
    }

    private boolean containsPageField(Schema<?> schema) {
        if (schema.getProperties() == null || schema.getProperties().isEmpty()) {
            return false;
        }
        if (!schema.getProperties().containsKey("code") || !schema.getProperties().containsKey("data")) {
            return false;
        }
        return PAGE_FIELDS.stream().anyMatch(schema.getProperties()::containsKey);
    }

    private boolean containsVoidDataField(Schema<?> schema) {
        if (schema.getProperties() == null || !schema.getProperties().containsKey(DATA_FIELD)) {
            return false;
        }
        if (!schema.getProperties().containsKey("code")) {
            return false;
        }
        Schema<?> dataSchema = schema.getProperties().get(DATA_FIELD);
        if (dataSchema == null || dataSchema.get$ref() != null) {
            return false;
        }
        if (dataSchema.getProperties() != null && !dataSchema.getProperties().isEmpty()) {
            return false;
        }
        if (dataSchema.getAdditionalProperties() != null) {
            return false;
        }
        return dataSchema.getType() == null || "object".equals(dataSchema.getType());
    }

    private Schema<?> resolveSchema(OpenAPI openApi, Schema<?> schema) {
        if (schema.get$ref() == null) {
            return schema;
        }
        String schemaName = schema.get$ref().replace("#/components/schemas/", "");
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return null;
        }
        return openApi.getComponents().getSchemas().get(schemaName);
    }
}
