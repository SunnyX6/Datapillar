package com.sunny.datapillar.studio.config.openapi;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI响应模型配置
 * 负责按接口类型裁剪分页字段文档显示
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
public class OpenApiConfig {

    private static final String PAGED_EXTENSION_KEY = "x-paged";
    private static final String TAG_GROUPS_EXTENSION_KEY = "x-tagGroups";
    private static final Set<String> PAGE_FIELDS = Set.of("limit", "offset", "total");
    private static final String DATA_FIELD = "data";
    private static final String CODE_FIELD = "code";
    private static final Integer SUCCESS_CODE = 0;
    private static final String SUCCESS_CODE_DESC = "业务状态码，0 表示成功";
    private static final Map<String, List<String>> MODULE_TAG_GROUPS = buildModuleTagGroups();

    @Bean
    public OperationCustomizer pagedOperationCustomizer() {
        return (operation, handlerMethod) -> markPagedOperation(operation, handlerMethod);
    }

    @Bean
    public OpenApiCustomizer apiResponseOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation ->
                            customizeOperationResponse(openApi, operation)));
            customizeTagGroups(openApi);
        };
    }

    private Operation markPagedOperation(Operation operation, HandlerMethod handlerMethod) {
        if (AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), OpenApiPaged.class)) {
            operation.addExtension(PAGED_EXTENSION_KEY, true);
        }
        return operation;
    }

    private void customizeOperationResponse(OpenAPI openApi, Operation operation) {
        if (isPagedOperation(operation) || operation.getResponses() == null) {
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
                adjustedSchema = normalizeSuccessCodeField(adjustedSchema);
                if (adjustedSchema != targetSchema) {
                    mediaType.setSchema(adjustedSchema);
                }
            }
        }
    }

    private boolean isPagedOperation(Operation operation) {
        Map<String, Object> extensions = operation.getExtensions();
        if (extensions == null || extensions.isEmpty()) {
            return false;
        }
        Object value = extensions.get(PAGED_EXTENSION_KEY);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
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
        if (!schema.getProperties().containsKey(CODE_FIELD) || !schema.getProperties().containsKey(DATA_FIELD)) {
            return false;
        }
        return PAGE_FIELDS.stream().anyMatch(schema.getProperties()::containsKey);
    }

    private boolean containsVoidDataField(Schema<?> schema) {
        if (schema.getProperties() == null || !schema.getProperties().containsKey(DATA_FIELD)) {
            return false;
        }
        if (!schema.getProperties().containsKey(CODE_FIELD)) {
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

    @SuppressWarnings("unchecked")
    private Schema<?> normalizeSuccessCodeField(Schema<?> schema) {
        if (schema.getProperties() == null || !schema.getProperties().containsKey(CODE_FIELD)) {
            return schema;
        }
        Schema<?> codeSchema = schema.getProperties().get(CODE_FIELD);
        if (codeSchema == null) {
            return schema;
        }

        boolean unchanged = SUCCESS_CODE.equals(codeSchema.getExample())
                && SUCCESS_CODE.equals(codeSchema.getDefault())
                && SUCCESS_CODE_DESC.equals(codeSchema.getDescription());
        if (unchanged) {
            return schema;
        }

        Schema<?> copied = Json.mapper().convertValue(schema, Schema.class);
        Map<String, Schema> properties = copied.getProperties();
        if (properties == null) {
            return schema;
        }
        Schema<?> copiedCodeSchema = properties.get(CODE_FIELD);
        if (copiedCodeSchema == null) {
            return schema;
        }
        copiedCodeSchema.setExample(SUCCESS_CODE);
        copiedCodeSchema.setDefault(SUCCESS_CODE);
        copiedCodeSchema.setDescription(SUCCESS_CODE_DESC);
        return copied;
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

    private void customizeTagGroups(OpenAPI openApi) {
        Set<String> existingTags = collectExistingTagNames(openApi);
        if (existingTags.isEmpty()) {
            return;
        }

        List<Map<String, Object>> tagGroups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : MODULE_TAG_GROUPS.entrySet()) {
            List<String> tags = entry.getValue().stream()
                    .filter(existingTags::contains)
                    .toList();
            if (tags.isEmpty()) {
                continue;
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", entry.getKey());
            group.put("tags", tags);
            tagGroups.add(group);
        }
        if (tagGroups.isEmpty()) {
            return;
        }
        openApi.addExtension(TAG_GROUPS_EXTENSION_KEY, tagGroups);
    }

    private Set<String> collectExistingTagNames(OpenAPI openApi) {
        Set<String> tags = new LinkedHashSet<>();
        if (openApi.getTags() != null) {
            for (Tag tag : openApi.getTags()) {
                if (tag != null && tag.getName() != null && !tag.getName().isBlank()) {
                    tags.add(tag.getName());
                }
            }
        }
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(pathItem -> {
                if (pathItem == null) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    if (operation == null || operation.getTags() == null) {
                        return;
                    }
                    operation.getTags().stream()
                            .filter(tagName -> tagName != null && !tagName.isBlank())
                            .forEach(tags::add);
                });
            });
        }
        return tags;
    }

    private static Map<String, List<String>> buildModuleTagGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("setup", List.of("系统初始化"));
        groups.put("tenant", List.of(
                "租户",
                "租户成员",
                "租户角色",
                "租户邀请",
                "租户功能",
                "租户SSO"));
        groups.put("user", List.of(
                "用户",
                "用户资料",
                "用户权限"));
        groups.put("project", List.of("项目"));
        groups.put("workflow", List.of(
                "工作流",
                "工作流DAG",
                "工作流运行",
                "工作流任务",
                "工作流依赖"));
        groups.put("llm", List.of("LLM"));
        groups.put("sql", List.of("SQL"));
        groups.put("system", List.of("健康检查"));
        return groups;
    }
}
