package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationBizService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "租户邀请业务", description = "租户邀请业务接口")
@RestController
@RequestMapping("/biz/tenants/{tenantId}/invitation")
@RequiredArgsConstructor
public class TenantInvitationBizController {

    private final TenantInvitationBizService tenantInvitationBizService;

    @Operation(summary = "接受邀请")
    @PostMapping("/accept")
    public ApiResponse<Map<String, Object>> accept(@PathVariable Long tenantId,
                                                   @Valid @RequestBody InvitationDto.AcceptRequest request) {
        tenantInvitationBizService.acceptInvitation(tenantId, request.getInviteCode());
        return ApiResponse.ok(Map.of());
    }
}
