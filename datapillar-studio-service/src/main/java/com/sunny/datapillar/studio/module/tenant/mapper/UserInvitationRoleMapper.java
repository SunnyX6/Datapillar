package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户邀请角色Mapper
 * 负责用户邀请角色数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserInvitationRoleMapper extends BaseMapper<UserInvitationRole> {
}
