package com.sunny.datapillar.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邀请关联角色 Mapper
 */
@Mapper
public interface UserInvitationRoleMapper {
    List<Long> selectRoleIdsByInvitationId(@Param("invitationId") Long invitationId);
}
