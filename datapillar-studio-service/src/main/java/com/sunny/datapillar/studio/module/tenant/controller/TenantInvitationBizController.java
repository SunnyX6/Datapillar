package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户邀请业务控制器
 * 负责租户邀请业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户邀请", description = "租户邀请接口")
@RestController
@RequestMapping("/biz/invitations")
@RequiredArgsConstructor
public class TenantInvitationBizController {

    private final TenantInvitationBizService tenantInvitationBizService;

    @Operation(summary = "根据邀请码查询邀请详情")
    @GetMapping("/{inviteCode}")
    public ApiResponse<InvitationDto.DetailResponse> detail(@PathVariable("inviteCode") String inviteCode) {
        return ApiResponse.ok(tenantInvitationBizService.getInvitationByCode(inviteCode));
    }

    @Operation(summary = "接受邀请并注册")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody InvitationDto.RegisterRequest request) {
        tenantInvitationBizService.registerInvitation(request);
        return ApiResponse.ok();
    }
}
