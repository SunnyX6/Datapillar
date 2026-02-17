package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
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

    List<FeatureEntitlementDto.PermissionLimit> selectPermissionLimits(@Param("tenantId") Long tenantId);
}
