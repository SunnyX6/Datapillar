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
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.module.llm.service.LlmBizService;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型业务控制器
 * 负责大模型业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "LLM", description = "LLM 接口")
@RestController
@RequestMapping("/biz/llms")
@RequiredArgsConstructor
public class LlmBizController {

    private final LlmBizService llmBizService;

    @Operation(summary = "获取当前用户模型")
    @GetMapping("/models")
    public ApiResponse<List<LlmUserModelPermissionResponse>> list() {
        Long currentUserId = getRequiredCurrentUserId();
        return ApiResponse.ok(llmBizService.listCurrentUserModelPermissions(
                currentUserId,
                true));
    }

    @Operation(summary = "设置当前用户默认模型")
    @PutMapping("/models/{aiModelId}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long aiModelId) {
        Long currentUserId = getRequiredCurrentUserId();
        llmBizService.setCurrentUserDefaultModel(currentUserId, aiModelId);
        return ApiResponse.ok();
    }

    private Long getRequiredCurrentUserId() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null || userId <= 0) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
        }
        return userId;
    }
}
