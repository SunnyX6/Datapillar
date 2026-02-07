package com.sunny.datapillar.studio.module.invitation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.invitation.dto.InvitationDto;
import com.sunny.datapillar.studio.module.invitation.entity.UserInvitation;
import com.sunny.datapillar.studio.module.invitation.service.InvitationService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
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
 * 租户邀请接口
 */
@Tag(name = "租户邀请", description = "租户邀请接口")
@RestController
@RequestMapping("/tenants/{tenantId}/invitations")
@RequiredArgsConstructor
public class TenantInvitationController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;

    private final InvitationService invitationService;

    @Operation(summary = "创建邀请")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<InvitationDto.CreateResponse> create(@PathVariable Long tenantId,
                                                            @Valid @RequestBody InvitationDto.Create dto) {
        return ApiResponse.ok(invitationService.createInvitation(dto));
    }

    @Operation(summary = "获取邀请列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<InvitationDto.Response>> list(@PathVariable Long tenantId,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) Integer limit,
                                                          @RequestParam(required = false) Integer offset) {
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        IPage<UserInvitation> page = invitationService.listInvitations(status, resolvedLimit, resolvedOffset);
        List<InvitationDto.Response> data = page.getRecords().stream()
                .map(invitation -> {
                    InvitationDto.Response response = new InvitationDto.Response();
                    BeanUtils.copyProperties(invitation, response);
                    return response;
                })
                .toList();
        return ApiResponse.page(data, resolvedLimit, resolvedOffset, page.getTotal());
    }

    @Operation(summary = "更新邀请")
    @PatchMapping("/{invitationId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @PathVariable Long invitationId,
                                    @Valid @RequestBody InvitationDto.ActionRequest request) {
        String action = request == null ? null : request.getAction();
        if (action != null && "CANCEL".equalsIgnoreCase(action)) {
            invitationService.cancelInvitation(invitationId);
            return ApiResponse.ok(null);
        }
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
    }
}
