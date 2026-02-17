package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.service.InvitationService;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户邀请业务服务实现
 * 实现租户邀请业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantInvitationBizServiceImpl implements TenantInvitationBizService {

    private final InvitationService invitationService;

    @Override
    public void acceptInvitation(Long tenantId, String inviteCode) {
        invitationService.acceptInvitation(tenantId, inviteCode);
    }
}
