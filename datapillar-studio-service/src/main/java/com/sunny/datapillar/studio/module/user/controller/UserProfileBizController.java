package com.sunny.datapillar.studio.module.user.controller;

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
import com.sunny.datapillar.studio.module.user.service.UserProfileBizService;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UserProfileBusiness controller Responsible userProfileBusiness interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "User profile", description = "Current user profile interface")
@RestController
@RequestMapping("/biz/users/me/profile")
@RequiredArgsConstructor
public class UserProfileBizController {

  private final UserProfileBizService userProfileBizService;

  @Operation(summary = "Get current user information")
  @GetMapping
  public ApiResponse<UserResponse> profile() {
    Long userId = UserContextUtil.getRequiredUserId();
    return ApiResponse.ok(userProfileBizService.getProfile(userId));
  }

  @Operation(summary = "Update current user profile")
  @PatchMapping
  public ApiResponse<Void> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
    Long userId = UserContextUtil.getRequiredUserId();
    userProfileBizService.updateProfile(userId, request);
    return ApiResponse.ok(null);
  }
}
