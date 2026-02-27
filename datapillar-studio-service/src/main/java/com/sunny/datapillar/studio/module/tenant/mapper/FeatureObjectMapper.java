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
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;

/**
 * 功能ObjectMapper
 * 负责功能Object数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface FeatureObjectMapper extends BaseMapper<FeatureObject> {

    List<FeatureObjectPermissionItem> selectFeatureObjectsAll(@Param("tenantId") Long tenantId);

    List<FeatureObjectPermissionItem> selectRoleObjectPermissionsAll(@Param("tenantId") Long tenantId,
                                                                           @Param("roleId") Long roleId);

    List<FeatureObjectPermissionItem> selectRoleObjectPermissionsAssigned(@Param("tenantId") Long tenantId,
                                                                                @Param("roleId") Long roleId);

    List<FeatureRoleSourceItem> selectUserRoleSources(@Param("tenantId") Long tenantId,
                                                       @Param("userId") Long userId);

    /**
     * 根据用户ID查询可访问的功能对象列表
     */
    List<FeatureObject> findByUserId(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("location") String location);

    /**
     * 查询所有可见的功能对象
     */
    List<FeatureObject> findAllVisible(@Param("tenantId") Long tenantId);

    List<TenantFeatureItem> selectFeatureEntitlements(@Param("tenantId") Long tenantId);

    TenantFeatureItem selectFeatureEntitlement(@Param("tenantId") Long tenantId,
                                                        @Param("objectId") Long objectId);
}
