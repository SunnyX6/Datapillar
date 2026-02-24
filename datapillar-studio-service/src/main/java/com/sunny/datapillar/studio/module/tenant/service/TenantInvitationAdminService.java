package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;

/**
 * 租户邀请管理服务
 * 提供租户邀请管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantInvitationAdminService {

    InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto);
}
