package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAuditAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户功能Audit管理控制器
 * 负责租户功能Audit管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户功能授权审计", description = "租户功能授权审计接口")
@RestController
@RequestMapping("/admin/tenants/{tenantId}/features/audits")
@RequiredArgsConstructor
public class TenantFeatureAuditAdminController {

    private final TenantFeatureAuditAdminService tenantFeatureAuditAdminService;

    @Operation(summary = "获取租户功能授权审计")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<TenantFeatureAudit>> audit(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantFeatureAuditAdminService.listAudits());
    }
}
