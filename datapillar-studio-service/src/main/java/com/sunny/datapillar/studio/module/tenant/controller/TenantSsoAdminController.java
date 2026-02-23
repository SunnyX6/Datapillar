package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantSsoAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sunny.datapillar.common.exception.BadRequestException;

/**
 * 租户单点登录管理控制器
 * 负责租户单点登录管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户SSO", description = "租户SSO接口")
@RestController
@RequestMapping("/admin/tenant/current/sso")
@RequiredArgsConstructor
public class TenantSsoAdminController {

    private static final String DINGTALK = "dingtalk";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private final TenantSsoAdminService tenantSsoAdminService;

    @Operation(summary = "获取SSO配置列表")
    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<SsoConfigDto.Response>> listConfigs() {
        return ApiResponse.ok(tenantSsoAdminService.listConfigs());
    }

    @Operation(summary = "创建SSO配置")
    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> createConfig(@Valid @RequestBody SsoConfigDto.Create dto) {
        if (dto == null) {
            throw new BadRequestException("参数错误");
        }
        String provider = normalizeProvider(dto.getProvider(), true);
        validateProvider(provider);
        validateStatus(dto.getStatus());
        validateDingtalkConfigForCreate(dto.getConfig());
        dto.setProvider(provider);
        tenantSsoAdminService.createConfig(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新SSO配置")
    @PatchMapping("/configs/{configId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateConfig(@PathVariable Long configId,
                                          @Valid @RequestBody SsoConfigDto.Update dto) {
        if (dto != null) {
            validateStatus(dto.getStatus());
            validateDingtalkConfigForUpdate(dto.getConfig());
        }
        tenantSsoAdminService.updateConfig(configId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询SSO绑定列表")
    @GetMapping("/identities")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<SsoIdentityDto.Item>> listIdentities(@RequestParam(required = false) String provider,
                                                                 @RequestParam(required = false) Long userId) {
        String normalizedProvider = normalizeProvider(provider, false);
        if (normalizedProvider != null) {
            validateProvider(normalizedProvider);
        }
        return ApiResponse.ok(tenantSsoAdminService.listIdentities(normalizedProvider, userId));
    }

    @Operation(summary = "通过授权码绑定SSO账号")
    @PostMapping("/identities/bind/code")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> bindByCode(@Valid @RequestBody SsoIdentityDto.BindByCodeRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        String provider = normalizeProvider(request.getProvider(), true);
        validateProvider(provider);
        request.setProvider(provider);
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

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BadRequestException("参数错误");
        }
    }

    private void validateProvider(String provider) {
        if (!DINGTALK.equals(provider)) {
            throw new BadRequestException("参数错误");
        }
    }

    private void validateDingtalkConfigForCreate(SsoConfigDto.DingtalkConfig config) {
        if (config == null) {
            throw new BadRequestException("参数错误");
        }
        requireText(config.getClientId());
        requireText(config.getClientSecret());
        requireText(config.getRedirectUri());
    }

    private void validateDingtalkConfigForUpdate(SsoConfigDto.DingtalkConfig config) {
        if (config == null) {
            return;
        }
        requireText(config.getClientId());
        requireText(config.getRedirectUri());
        if (config.getClientSecret() != null && !StringUtils.hasText(config.getClientSecret())) {
            throw new BadRequestException("参数错误");
        }
    }

    private void requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("参数错误");
        }
    }

    private String normalizeProvider(String provider, boolean required) {
        if (!StringUtils.hasText(provider)) {
            if (required) {
                throw new BadRequestException("参数错误");
            }
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
