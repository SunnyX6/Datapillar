package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.service.TenantAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户管理控制器
 * 负责租户管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户管理", description = "租户管理接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    @Operation(summary = "获取租户列表")
    @GetMapping("/tenants")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<TenantDto.Response>> list(@RequestParam(required = false) Integer status) {
        List<Tenant> tenants = tenantAdminService.listTenants(status);
        List<TenantDto.Response> data = tenants.stream()
                .map(tenant -> {
                    TenantDto.Response response = new TenantDto.Response();
                    BeanUtils.copyProperties(tenant, response);
                    return response;
                })
                .toList();
        return ApiResponse.ok(data);
    }

    @Operation(summary = "创建租户")
    @PostMapping("/tenant")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> create(@Valid @RequestBody TenantDto.Create dto) {
        tenantAdminService.createTenant(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取租户详情")
    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<TenantDto.Response> detail(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantAdminService.getTenant(tenantId));
    }

    @Operation(summary = "更新租户信息")
    @PatchMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @Valid @RequestBody TenantDto.Update dto) {
        tenantAdminService.updateTenant(tenantId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新租户状态")
    @PatchMapping("/tenant/{tenantId}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long tenantId,
                                          @RequestBody TenantDto.StatusUpdate dto) {
        Integer status = dto == null ? null : dto.getStatus();
        tenantAdminService.updateStatus(tenantId, status);
        return ApiResponse.ok();
    }
}
