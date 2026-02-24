package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.service.InvitationService;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户邀请管理服务实现
 * 实现租户邀请管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantInvitationAdminServiceImpl implements TenantInvitationAdminService {

    private final InvitationService invitationService;

    @Override
    public InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto) {
        return invitationService.createInvitation(dto);
    }
}
