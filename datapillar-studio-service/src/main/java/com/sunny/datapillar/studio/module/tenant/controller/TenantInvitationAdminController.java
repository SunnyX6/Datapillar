package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationAdminService;
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
import com.sunny.datapillar.common.exception.BadRequestException;

/**
 * 租户邀请管理控制器
 * 负责租户邀请管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户邀请", description = "租户邀请接口")
@RestController
@RequestMapping("/admin/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantInvitationAdminController {

    private final TenantInvitationAdminService tenantInvitationAdminService;

    @Operation(summary = "创建邀请")
    @PostMapping("/invitation")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> create(@PathVariable Long tenantId,
                                    @Valid @RequestBody InvitationDto.Create dto) {
        tenantInvitationAdminService.createInvitation(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取邀请列表")
    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<InvitationDto.Response>> list(@PathVariable Long tenantId,
                                                          @RequestParam(required = false) Integer status) {
        List<UserInvitation> invitations = tenantInvitationAdminService.listInvitations(status);
        List<InvitationDto.Response> data = invitations.stream()
                .map(invitation -> {
                    InvitationDto.Response response = new InvitationDto.Response();
                    BeanUtils.copyProperties(invitation, response);
                    return response;
                })
                .toList();
        return ApiResponse.ok(data);
    }

    @Operation(summary = "更新邀请")
    @PatchMapping("/invitation/{invitationId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @PathVariable Long invitationId,
                                    @Valid @RequestBody InvitationDto.ActionRequest request) {
        String action = request == null ? null : request.getAction();
        if (action != null && "CANCEL".equalsIgnoreCase(action)) {
            tenantInvitationAdminService.cancelInvitation(invitationId);
            return ApiResponse.ok();
        }
        throw new BadRequestException("参数错误");
    }

}
