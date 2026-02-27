package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.SsoConfigUpdateRequest;
import com.sunny.datapillar.studio.dto.tenant.request.SsoIdentityBindByCodeRequest;
import com.sunny.datapillar.studio.dto.tenant.response.SsoConfigResponse;
import com.sunny.datapillar.studio.dto.tenant.response.SsoIdentityItem;
import com.sunny.datapillar.studio.module.tenant.service.TenantSsoAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户单点登录管理控制器
 * 负责租户单点登录管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户SSO", description = "租户SSO接口")
@RestController
@Validated
@RequestMapping("/admin/tenant/current/sso")
@RequiredArgsConstructor
public class TenantSsoAdminController {

    private final TenantSsoAdminService tenantSsoAdminService;

    @Operation(summary = "获取SSO配置列表")
    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<SsoConfigResponse>> listConfigs() {
        return ApiResponse.ok(tenantSsoAdminService.listConfigs());
    }

    @Operation(summary = "创建SSO配置")
    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> createConfig(@Valid @RequestBody SsoConfigCreateRequest dto) {
        dto.setProvider(normalizeProvider(dto.getProvider()));
        tenantSsoAdminService.createConfig(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新SSO配置")
    @PatchMapping("/configs/{configId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateConfig(@PathVariable Long configId,
                                          @Valid @RequestBody SsoConfigUpdateRequest dto) {
        tenantSsoAdminService.updateConfig(configId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询SSO绑定列表")
    @GetMapping("/identities")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<SsoIdentityItem>> listIdentities(
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^dingtalk$", message = "参数错误") String provider,
            @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(tenantSsoAdminService.listIdentities(normalizeOptionalProvider(provider), userId));
    }

    @Operation(summary = "通过授权码绑定SSO账号")
    @PostMapping("/identities/bind/code")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> bindByCode(@Valid @RequestBody SsoIdentityBindByCodeRequest request) {
        request.setProvider(normalizeProvider(request.getProvider()));
        tenantSsoAdminService.bindByCode(request);
        return ApiResponse.ok();
    }

    @Operation(summary = "解绑SSO账号")
    @DeleteMapping("/identities/{identityId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> unbind(@PathVariable Long identityId) {
        tenantSsoAdminService.unbind(identityId);
        return ApiResponse.ok();
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return provider;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
