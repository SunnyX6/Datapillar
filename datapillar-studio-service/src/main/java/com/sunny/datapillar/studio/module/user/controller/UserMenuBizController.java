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
import com.sunny.datapillar.studio.module.user.service.UserPermissionService;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User menu business controller Responsible for user menu business interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-03-02
 */
@Tag(name = "User menu", description = "Current user menu interface")
@RestController
@RequestMapping("/biz/users/me/menu")
@RequiredArgsConstructor
public class UserMenuBizController {

  private final UserPermissionService userPermissionService;

  @Operation(summary = "Get current user menu")
  @GetMapping
  public ApiResponse<List<UserMenuItem>> listMyMenus() {
    Long userId = UserContextUtil.getRequiredUserId();
    return ApiResponse.ok(userPermissionService.listMenus(userId));
  }
}
