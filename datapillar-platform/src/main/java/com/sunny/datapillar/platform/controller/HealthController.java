package com.sunny.datapillar.platform.controller;

import com.sunny.datapillar.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 * 提供服务健康状态检查接口
 *
 * @author Sunny
 * @since 2026-02-03
 */
@Tag(name = "健康检查", description = "服务健康状态检查接口")
@RestController
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("OK");
    }
}
