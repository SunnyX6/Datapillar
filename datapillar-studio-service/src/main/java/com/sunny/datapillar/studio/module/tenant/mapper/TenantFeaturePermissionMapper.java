package com.sunny.datapillar.studio.module.tenant.mapper;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeaturePermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户功能权限Mapper
 * 负责租户功能权限数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantFeaturePermissionMapper extends BaseMapper<TenantFeaturePermission> {

    TenantFeaturePermission selectByTenantIdAndObjectId(@Param("tenantId") Long tenantId,
                                                        @Param("objectId") Long objectId);

    List<TenantFeaturePermission> selectByTenantId(@Param("tenantId") Long tenantId);

    List<TenantFeaturePermissionLimitItem> selectPermissionLimits(@Param("tenantId") Long tenantId);
}
