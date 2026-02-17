package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import java.util.List;

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
     * 查询邀请列表（租户管理员操作）。
     */
    List<UserInvitation> listInvitations(Integer status);

    /**
     * 取消邀请（租户管理员操作）。
     */
    void cancelInvitation(Long invitationId);

    /**
     * 邀请接受（被邀请用户操作）。
     *
     * @param tenantId 目标租户
     * @param inviteCode 邀请码
     */
    void acceptInvitation(Long tenantId, String inviteCode);
}
