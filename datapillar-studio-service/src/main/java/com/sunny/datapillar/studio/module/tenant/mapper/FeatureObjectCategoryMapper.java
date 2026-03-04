package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObjectCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * FunctionObjectCategoryMapper Responsible for functionObjectCategoryData access and persistence
 * mapping
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
