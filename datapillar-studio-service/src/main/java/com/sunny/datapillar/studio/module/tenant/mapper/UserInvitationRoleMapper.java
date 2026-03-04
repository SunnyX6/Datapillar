package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * User invitation roleMapper Responsible for user invitation role data access and persistence
 * mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserInvitationRoleMapper extends BaseMapper<UserInvitationRole> {}
