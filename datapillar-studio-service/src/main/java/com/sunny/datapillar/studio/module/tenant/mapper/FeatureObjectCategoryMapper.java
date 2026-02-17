package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObjectCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 功能ObjectCategoryMapper
 * 负责功能ObjectCategory数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface FeatureObjectCategoryMapper extends BaseMapper<FeatureObjectCategory> {

    FeatureObjectCategory selectByCode(@Param("code") String code);

    FeatureObjectCategory selectByName(@Param("name") String name);

    FeatureObjectCategory selectByCategoryId(@Param("id") Long id);

    List<FeatureObjectCategory> selectAll();
}
