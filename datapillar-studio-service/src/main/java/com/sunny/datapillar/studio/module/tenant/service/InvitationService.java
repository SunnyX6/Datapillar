package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;

/**
 * 邀请服务
 * 提供邀请业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface InvitationService {

    /**
     * 创建邀请（租户管理员操作）。
     */
    InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto);

    /**
     * 根据邀请码查询邀请详情（匿名可访问）。
     */
    InvitationDto.DetailResponse getInvitationByCode(String inviteCode);

    /**
     * 邀请注册（匿名用户操作）。
     */
    void registerInvitation(InvitationDto.RegisterRequest request);
}
