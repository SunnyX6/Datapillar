package com.sunny.datapillar.studio.module.llm.controller;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.service.LlmBizService;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sunny.datapillar.common.exception.UnauthorizedException;

/**
 * 大模型业务控制器
 * 负责大模型业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "LLM业务接口", description = "当前用户模型查询与默认模型设置")
@RestController
@RequestMapping("/biz/llms")
@RequiredArgsConstructor
public class LlmBizController {

    private final LlmBizService llmBizService;

    @Operation(summary = "获取当前用户模型")
    @GetMapping("/models")
    public ApiResponse<List<LlmManagerDto.ModelUsageResponse>> list() {
        Long currentUserId = getRequiredCurrentUserId();
        return ApiResponse.ok(llmBizService.listCurrentUserModelUsages(
                currentUserId,
                true));
    }

    @Operation(summary = "设置当前用户默认模型")
    @PutMapping("/model/{modelId}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long modelId) {
        Long currentUserId = getRequiredCurrentUserId();
        llmBizService.setCurrentUserDefaultModel(currentUserId, modelId);
        return ApiResponse.ok();
    }

    private Long getRequiredCurrentUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("未授权访问");
        }
        return userId;
    }
}
