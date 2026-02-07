package com.sunny.datapillar.studio.module.sso.controller;

import com.sunny.datapillar.studio.module.sso.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.sso.service.SsoConfigService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户 SSO 配置接口
 */
@Tag(name = "租户SSO配置", description = "租户SSO配置接口")
@RestController
@RequestMapping("/tenants/{tenantId}/sso-configs")
@RequiredArgsConstructor
public class TenantSsoConfigController {

    private final SsoConfigService ssoConfigService;

    @Operation(summary = "获取SSO配置列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<SsoConfigDto.Response>> list(@PathVariable Long tenantId) {
        return ApiResponse.ok(ssoConfigService.listConfigs());
    }

    @Operation(summary = "创建SSO配置")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long tenantId,
                                                 @Valid @RequestBody SsoConfigDto.Create dto) {
        Long id = ssoConfigService.createConfig(dto);
        return ApiResponse.ok(Map.of("configId", id));
    }

    @Operation(summary = "更新SSO配置")
    @PatchMapping("/{configId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @PathVariable Long configId,
                                    @Valid @RequestBody SsoConfigDto.Update dto) {
        ssoConfigService.updateConfig(configId, dto);
        return ApiResponse.ok(null);
    }
}
