package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户邀请管理控制器
 * 负责租户邀请管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户邀请", description = "租户邀请接口")
@RestController
@RequestMapping("/admin/tenant/current/invitations")
@RequiredArgsConstructor
public class TenantInvitationAdminController {

    private final TenantInvitationAdminService tenantInvitationAdminService;

    @Operation(summary = "创建邀请")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<InvitationDto.CreateResponse> create(@Valid @RequestBody InvitationDto.Create dto) {
        return ApiResponse.ok(tenantInvitationAdminService.createInvitation(dto));
    }
}
