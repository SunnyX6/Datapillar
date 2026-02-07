package com.sunny.datapillar.studio.module.invitation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.invitation.entity.UserInvitation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户邀请 Mapper
 */
@Mapper
public interface UserInvitationMapper extends BaseMapper<UserInvitation> {
}
