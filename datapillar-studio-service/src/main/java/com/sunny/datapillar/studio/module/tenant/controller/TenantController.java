package com.sunny.datapillar.studio.module.tenant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * 租户管理接口
 */
@Tag(name = "租户管理", description = "租户管理接口")
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;

    private final TenantService tenantService;

    @Operation(summary = "获取租户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<TenantDto.Response>> list(@RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) Integer limit,
                                                      @RequestParam(required = false) Integer offset) {
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        IPage<Tenant> page = tenantService.listTenants(status, resolvedLimit, resolvedOffset);
        List<TenantDto.Response> data = page.getRecords().stream()
                .map(tenant -> {
                    TenantDto.Response response = new TenantDto.Response();
                    BeanUtils.copyProperties(tenant, response);
                    return response;
                })
                .toList();
        return ApiResponse.page(data, resolvedLimit, resolvedOffset, page.getTotal());
    }

    @Operation(summary = "创建租户")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody TenantDto.Create dto) {
        Long id = tenantService.createTenant(dto);
        return ApiResponse.ok(Map.of("tenantId", id));
    }

    @Operation(summary = "获取租户详情")
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<TenantDto.Response> detail(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantService.getTenant(tenantId));
    }

    @Operation(summary = "更新租户信息")
    @PatchMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @Valid @RequestBody TenantDto.Update dto) {
        tenantService.updateTenant(tenantId, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "更新租户状态")
    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long tenantId,
                                          @RequestBody TenantDto.StatusUpdate dto) {
        Integer status = dto == null ? null : dto.getStatus();
        tenantService.updateStatus(tenantId, status);
        return ApiResponse.ok(null);
    }
}
