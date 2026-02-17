package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户邀请Mapper
 * 负责用户邀请数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserInvitationMapper extends BaseMapper<UserInvitation> {
}
