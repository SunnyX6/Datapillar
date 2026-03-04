package com.sunny.datapillar.studio.module.llm.controller;

import com.sunny.datapillar.common.response.ApiResponse;
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
 * Large model business controller Responsible for large model business interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "LLM", description = "LLM interface")
@RestController
@RequestMapping("/biz/llms")
@RequiredArgsConstructor
public class LlmBizController {

  private final LlmBizService llmBizService;

  @Operation(summary = "Get the current user model")
  @GetMapping("/models")
  public ApiResponse<List<LlmUserModelPermissionResponse>> list() {
    Long currentUserId = getRequiredCurrentUserId();
    return ApiResponse.ok(llmBizService.listCurrentUserModelPermissions(currentUserId, true));
  }

  @Operation(summary = "Set the current users default model")
  @PutMapping("/models/{aiModelId}/default")
  public ApiResponse<Void> setDefault(@PathVariable Long aiModelId) {
    Long currentUserId = getRequiredCurrentUserId();
    llmBizService.setCurrentUserDefaultModel(currentUserId, aiModelId);
    return ApiResponse.ok();
  }

  private Long getRequiredCurrentUserId() {
    Long userId = UserContextUtil.getUserId();
    if (userId == null || userId <= 0) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Unauthorized access");
    }
    return userId;
  }
}
