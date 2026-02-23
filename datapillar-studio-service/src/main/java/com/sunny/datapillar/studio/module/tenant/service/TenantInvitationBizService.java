package com.sunny.datapillar.studio.module.tenant.service;

/**
 * 租户邀请业务服务
 * 提供租户邀请业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantInvitationBizService {

    void acceptInvitation(String inviteCode);
}
