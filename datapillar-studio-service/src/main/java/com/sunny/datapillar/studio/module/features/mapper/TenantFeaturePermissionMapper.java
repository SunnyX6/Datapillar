package com.sunny.datapillar.studio.module.features.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.features.entity.TenantFeaturePermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户功能授权 Mapper
 */
@Mapper
public interface TenantFeaturePermissionMapper extends BaseMapper<TenantFeaturePermission> {

    TenantFeaturePermission selectByTenantIdAndObjectId(@Param("tenantId") Long tenantId,
                                                        @Param("objectId") Long objectId);

    List<TenantFeaturePermission> selectByTenantId(@Param("tenantId") Long tenantId);

    List<FeatureEntitlementDto.PermissionLimit> selectPermissionLimits(@Param("tenantId") Long tenantId);
}
