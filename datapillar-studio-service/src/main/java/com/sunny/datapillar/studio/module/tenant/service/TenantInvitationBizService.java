package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;

/**
 * 租户邀请业务服务
 * 提供租户邀请业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantInvitationBizService {

    InvitationDto.DetailResponse getInvitationByCode(String inviteCode);

    void registerInvitation(InvitationDto.RegisterRequest request);
}
