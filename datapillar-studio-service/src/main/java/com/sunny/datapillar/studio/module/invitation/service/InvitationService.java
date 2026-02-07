package com.sunny.datapillar.studio.module.invitation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.invitation.dto.InvitationDto;
import com.sunny.datapillar.studio.module.invitation.entity.UserInvitation;

/**
 * 邀请服务
 */
public interface InvitationService {

    InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto);

    IPage<UserInvitation> listInvitations(Integer status, int limit, int offset);

    void cancelInvitation(Long invitationId);
}
