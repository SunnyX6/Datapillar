package com.sunny.datapillar.platform.module.features.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.platform.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto.RoleSource;
import com.sunny.datapillar.platform.module.features.entity.FeatureObject;

/**
 * 功能对象 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface FeatureObjectMapper extends BaseMapper<FeatureObject> {

    List<FeatureObjectDto.ObjectPermission> selectFeatureObjectsAll(@Param("tenantId") Long tenantId);

    List<FeatureObjectDto.ObjectPermission> selectRoleObjectPermissionsAll(@Param("tenantId") Long tenantId,
                                                                          @Param("roleId") Long roleId);

    List<FeatureObjectDto.ObjectPermission> selectRoleObjectPermissionsAssigned(@Param("tenantId") Long tenantId,
                                                                               @Param("roleId") Long roleId);

    List<RoleSource> selectUserRoleSources(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    List<FeatureObjectDto.Assignment> selectUserOverridePermissions(@Param("tenantId") Long tenantId,
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

    List<FeatureEntitlementDto.Item> selectFeatureEntitlements(@Param("tenantId") Long tenantId);

    FeatureEntitlementDto.Item selectFeatureEntitlement(@Param("tenantId") Long tenantId,
                                                        @Param("objectId") Long objectId);
}
