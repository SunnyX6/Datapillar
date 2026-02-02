package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.OrgUser;

/**
 * 组织成员 Mapper
 */
@Mapper
public interface OrgUserMapper extends BaseMapper<OrgUser> {
}
