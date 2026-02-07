package com.sunny.datapillar.studio.module.features.controller;

import com.sunny.datapillar.studio.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.features.service.FeatureEntitlementService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
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
 * 功能授权管理控制器
 */
@Tag(name = "功能授权", description = "租户功能授权相关接口")
@RestController
@RequestMapping("/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureEntitlementService featureEntitlementService;

    @Operation(summary = "获取租户功能授权列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureEntitlementDto.Item>> list() {
        List<FeatureEntitlementDto.Item> items = featureEntitlementService.listEntitlements();
        return ApiResponse.ok(items);
    }

    @Operation(summary = "获取单个功能授权")
    @GetMapping("/{featureId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<FeatureEntitlementDto.Item> detail(@PathVariable Long featureId) {
        FeatureEntitlementDto.Item item = featureEntitlementService.getEntitlement(featureId);
        return ApiResponse.ok(item);
    }

    @Operation(summary = "更新单个功能授权")
    @PutMapping("/{featureId}/entitlement")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long featureId,
                                    @Valid @RequestBody FeatureEntitlementDto.UpdateItem item) {
        featureEntitlementService.updateEntitlement(featureId, item);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "批量更新租户功能授权")
    @PutMapping("/entitlements")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> batchUpdate(@Valid @RequestBody FeatureEntitlementDto.UpdateRequest request) {
        List<FeatureEntitlementDto.UpdateItem> items = request == null ? null : request.getItems();
        featureEntitlementService.updateEntitlements(items);
        return ApiResponse.ok(null);
    }
}
