package com.sunny.datapillar.studio.module.llm.controller;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.service.LlmAdminService;
import com.sunny.datapillar.studio.module.llm.service.LlmConnectionService;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型管理控制器
 * 负责大模型管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "LLM", description = "LLM 接口")
@RestController
@RequestMapping("/admin/llms")
@RequiredArgsConstructor
public class LLMAdminController {

    private final LlmAdminService llmAdminService;
    private final LlmConnectionService llmConnectionService;

    @Operation(summary = "获取模型池列表")
    @GetMapping("/models")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<LlmModelResponse>> listModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "provider") String providerCode,
            @RequestParam(required = false, name = "model_type") AiModelType modelType) {
        return ApiResponse.ok(llmAdminService.listModels(
                keyword,
                providerCode,
                modelType,
                getRequiredUserId()));
    }

    @Operation(summary = "获取模型池详情")
    @GetMapping("/models/{aiModelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmModelResponse> getModel(@PathVariable Long aiModelId) {
        return ApiResponse.ok(llmAdminService.getModel(getRequiredUserId(), aiModelId));
    }

    @Operation(summary = "创建模型")
    @PostMapping("/models")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmModelResponse> createModel(
            @Valid @RequestBody LlmModelCreateRequest request) {
        return ApiResponse.ok(llmAdminService.createModel(getRequiredUserId(), request));
    }

    @Operation(summary = "更新模型")
    @PatchMapping("/models/{aiModelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateModel(
            @PathVariable Long aiModelId,
            @Valid @RequestBody LlmModelUpdateRequest request) {
        llmAdminService.updateModel(getRequiredUserId(), aiModelId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/models/{aiModelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteModel(@PathVariable Long aiModelId) {
        llmAdminService.deleteModel(getRequiredUserId(), aiModelId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取供应商列表")
    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<LlmProviderResponse>> listProviders() {
        return ApiResponse.ok(llmAdminService.listProviders());
    }

    @Operation(summary = "创建供应商")
    @PostMapping("/provider")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> createProvider(
            @Valid @RequestBody LlmProviderCreateRequest request) {
        llmAdminService.createProvider(getRequiredUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新供应商")
    @PatchMapping("/provider/{providerCode}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody LlmProviderUpdateRequest request) {
        llmAdminService.updateProvider(getRequiredUserId(), providerCode, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除供应商")
    @DeleteMapping("/provider/{providerCode}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteProvider(@PathVariable String providerCode) {
        llmAdminService.deleteProvider(getRequiredUserId(), providerCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "连接模型")
    @PostMapping("/models/{aiModelId}/connect")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmModelConnectResponse> connectModel(
            @PathVariable Long aiModelId,
            @Valid @RequestBody LlmModelConnectRequest request) {
        return ApiResponse.ok(llmConnectionService.connectModel(getRequiredUserId(), aiModelId, request));
    }

    @Operation(summary = "获取用户模型授权")
    @GetMapping("/users/{userId}/models")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<LlmUserModelPermissionResponse>> listUserModelPermissions(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean onlyEnabled) {
        Long operatorUserId = getRequiredCurrentUserId();
        return ApiResponse.ok(llmAdminService.listUserModelPermissions(
                operatorUserId,
                userId,
                onlyEnabled));
    }

    @Operation(summary = "设置用户模型授权")
    @PutMapping("/users/{userId}/models/{aiModelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> upsertUserModelGrant(
            @PathVariable Long userId,
            @PathVariable Long aiModelId,
            @Valid @RequestBody LlmUserModelGrantRequest request) {
        Long operatorUserId = getRequiredCurrentUserId();
        llmAdminService.upsertUserModelGrant(operatorUserId, userId, aiModelId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除用户模型授权")
    @DeleteMapping("/users/{userId}/models/{aiModelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteUserModelGrant(@PathVariable Long userId, @PathVariable Long aiModelId) {
        Long operatorUserId = getRequiredCurrentUserId();
        llmAdminService.deleteUserModelGrant(operatorUserId, userId, aiModelId);
        return ApiResponse.ok();
    }

    private Long getRequiredUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
        }
        return userId;
    }

    private Long getRequiredCurrentUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null || userId <= 0) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
        }
        return userId;
    }
}
