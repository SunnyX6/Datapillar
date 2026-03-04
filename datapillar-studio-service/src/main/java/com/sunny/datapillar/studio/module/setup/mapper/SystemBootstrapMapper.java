package com.sunny.datapillar.studio.module.setup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * System bootMapper Responsible for system guidance data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface SystemBootstrapMapper extends BaseMapper<SystemBootstrap> {

  @Select(
      "SELECT id, setup_completed, setup_tenant_id, setup_admin_user_id, setup_token_hash, setup_token_generated_at, setup_completed_at, created_at, updated_at "
          + "FROM system_bootstrap WHERE id = #{id} FOR UPDATE")
  SystemBootstrap selectByIdForUpdate(@Param("id") Integer id);
}
