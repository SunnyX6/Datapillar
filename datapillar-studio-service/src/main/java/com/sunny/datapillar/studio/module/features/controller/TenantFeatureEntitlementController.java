package com.sunny.datapillar.studio.module.features.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.features.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.features.service.FeatureAuditService;
import com.sunny.datapillar.studio.module.features.service.FeatureEntitlementService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户功能授权接口
 */
@Tag(name = "租户功能授权", description = "租户功能授权接口")
@RestController
@RequestMapping("/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantFeatureEntitlementController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;

    private final FeatureEntitlementService featureEntitlementService;
    private final FeatureAuditService featureAuditService;

    @Operation(summary = "获取租户功能授权列表")
    @GetMapping("/features")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureEntitlementDto.Item>> list(@PathVariable Long tenantId) {
        return ApiResponse.ok(featureEntitlementService.listEntitlements());
    }

    @Operation(summary = "更新租户功能授权")
    @PutMapping("/features")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @RequestBody List<FeatureEntitlementDto.UpdateItem> items) {
        featureEntitlementService.updateEntitlements(items);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取租户功能授权审计")
    @GetMapping("/feature-audit")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<TenantFeatureAudit>> audit(@PathVariable Long tenantId,
                                                       @RequestParam(required = false) Integer limit,
                                                       @RequestParam(required = false) Integer offset) {
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        IPage<TenantFeatureAudit> page = featureAuditService.listAudits(resolvedLimit, resolvedOffset);
        return ApiResponse.page(page.getRecords(), resolvedLimit, resolvedOffset, page.getTotal());
    }
}
