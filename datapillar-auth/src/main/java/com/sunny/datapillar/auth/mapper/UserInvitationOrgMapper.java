package com.sunny.datapillar.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邀请关联组织 Mapper
 */
@Mapper
public interface UserInvitationOrgMapper {
    List<Long> selectOrgIdsByInvitationId(@Param("invitationId") Long invitationId);
}
