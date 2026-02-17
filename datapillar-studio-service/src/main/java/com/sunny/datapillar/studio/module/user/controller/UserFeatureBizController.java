package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.service.UserFeatureBizService;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户功能业务控制器
 * 负责用户功能业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户功能对象", description = "当前用户功能对象接口")
@RestController
@RequestMapping("/biz/users/me/features")
@RequiredArgsConstructor
public class UserFeatureBizController {

    private final UserFeatureBizService userFeatureBizService;

    @Operation(summary = "获取当前用户功能对象")
    @GetMapping
    public ApiResponse<List<FeatureObjectDto.TreeNode>> list(
            @RequestParam(value = "location", required = false) String location) {
        Long userId = UserContextUtil.getRequiredUserId();
        return ApiResponse.ok(userFeatureBizService.listFeatures(userId, location));
    }
}
