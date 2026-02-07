package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户 Mapper
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    Tenant selectByCode(@Param("code") String code);

    int updateHierarchy(@Param("oldPath") String oldPath,
                        @Param("newPath") String newPath,
                        @Param("delta") int delta);

    int updateEncryptPublicKey(@Param("id") Long id,
                               @Param("encryptPublicKey") String encryptPublicKey);
}
