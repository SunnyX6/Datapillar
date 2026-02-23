package com.sunny.datapillar.studio.module.llm.controller;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.dto.LlmProviderDto;
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
    public ApiResponse<List<LlmManagerDto.ModelResponse>> listModels(
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
    @GetMapping("/models/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmManagerDto.ModelResponse> getModel(@PathVariable Long modelId) {
        return ApiResponse.ok(llmAdminService.getModel(getRequiredUserId(), modelId));
    }

    @Operation(summary = "创建模型")
    @PostMapping("/model")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmManagerDto.ModelResponse> createModel(
            @Valid @RequestBody LlmManagerDto.CreateRequest request) {
        return ApiResponse.ok(llmAdminService.createModel(getRequiredUserId(), request));
    }

    @Operation(summary = "更新模型")
    @PatchMapping("/model/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateModel(
            @PathVariable Long modelId,
            @Valid @RequestBody LlmManagerDto.UpdateRequest request) {
        llmAdminService.updateModel(getRequiredUserId(), modelId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/model/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteModel(@PathVariable Long modelId) {
        llmAdminService.deleteModel(getRequiredUserId(), modelId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取供应商列表")
    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<LlmManagerDto.ProviderResponse>> listProviders() {
        return ApiResponse.ok(llmAdminService.listProviders());
    }

    @Operation(summary = "创建供应商")
    @PostMapping("/provider")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> createProvider(
            @Valid @RequestBody LlmProviderDto.CreateRequest request) {
        llmAdminService.createProvider(getRequiredUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新供应商")
    @PatchMapping("/provider/{providerCode}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody LlmProviderDto.UpdateRequest request) {
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
    @PostMapping("/model/{modelId}/connect")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmManagerDto.ConnectResponse> connectModel(
            @PathVariable Long modelId,
            @Valid @RequestBody LlmManagerDto.ConnectRequest request) {
        return ApiResponse.ok(llmConnectionService.connectModel(getRequiredUserId(), modelId, request));
    }

    @Operation(summary = "获取用户模型授权")
    @GetMapping("/users/{userId}/models")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<LlmManagerDto.ModelUsageResponse>> listUserModelUsages(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean onlyEnabled) {
        Long operatorUserId = getRequiredCurrentUserId();
        return ApiResponse.ok(llmAdminService.listUserModelUsages(
                operatorUserId,
                userId,
                onlyEnabled));
    }

    @Operation(summary = "设置用户模型授权")
    @PutMapping("/users/{userId}/model/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<LlmManagerDto.ModelUsageResponse> upsertUserModelGrant(
            @PathVariable Long userId,
            @PathVariable Long modelId,
            @Valid @RequestBody LlmManagerDto.ModelGrantRequest request) {
        Long operatorUserId = getRequiredCurrentUserId();
        return ApiResponse.ok(llmAdminService.upsertUserModelGrant(operatorUserId, userId, modelId, request));
    }

    @Operation(summary = "删除用户模型授权")
    @DeleteMapping("/users/{userId}/model/{modelId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteUserModelGrant(@PathVariable Long userId, @PathVariable Long modelId) {
        Long operatorUserId = getRequiredCurrentUserId();
        llmAdminService.deleteUserModelGrant(operatorUserId, userId, modelId);
        return ApiResponse.ok();
    }

    private Long getRequiredUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("未授权访问");
        }
        return userId;
    }

    private Long getRequiredCurrentUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("未授权访问");
        }
        return userId;
    }
}
