package com.sunny.datapillar.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.UserInvitation;

/**
 * 用户邀请 Mapper
 */
@Mapper
public interface UserInvitationMapper extends BaseMapper<UserInvitation> {
    UserInvitation selectByInviteCode(@Param("inviteCode") String inviteCode);

    int acceptInvitation(@Param("id") Long id,
                         @Param("userId") Long userId,
                         @Param("acceptedAt") LocalDateTime acceptedAt,
                         @Param("now") LocalDateTime now);
}
