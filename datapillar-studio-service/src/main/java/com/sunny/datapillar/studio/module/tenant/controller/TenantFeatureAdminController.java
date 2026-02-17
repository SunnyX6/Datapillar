package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户功能管理控制器
 * 负责租户功能管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户功能授权", description = "租户功能授权接口")
@RestController
@RequestMapping("/admin/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantFeatureAdminController {

    private final TenantFeatureAdminService tenantFeatureAdminService;

    @Operation(summary = "获取租户功能授权列表")
    @GetMapping("/features")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureEntitlementDto.Item>> list(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantFeatureAdminService.listEntitlements());
    }

    @Operation(summary = "更新租户功能授权")
    @PutMapping("/features")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @Valid @RequestBody List<FeatureEntitlementDto.UpdateItem> items) {
        tenantFeatureAdminService.updateEntitlements(items);
        return ApiResponse.ok(null);
    }
}
