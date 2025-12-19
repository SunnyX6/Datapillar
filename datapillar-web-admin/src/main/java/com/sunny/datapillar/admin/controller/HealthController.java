package com.sunny.datapillar.admin.controller;

import com.sunny.datapillar.admin.response.WebAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 * 提供服务健康状态检查接口
 *
 * @author sunny
 * @since 2024-01-01
 */
@Tag(name = "健康检查", description = "服务健康状态检查接口")
@RestController
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public WebAdminResponse<String> health() {
        return WebAdminResponse.ok("OK");
    }
}
