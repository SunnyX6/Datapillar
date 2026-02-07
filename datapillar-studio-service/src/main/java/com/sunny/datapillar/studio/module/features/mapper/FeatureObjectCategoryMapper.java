package com.sunny.datapillar.studio.module.features.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.features.entity.FeatureObjectCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 功能对象分类 Mapper
 */
@Mapper
public interface FeatureObjectCategoryMapper extends BaseMapper<FeatureObjectCategory> {

    FeatureObjectCategory selectByCode(@Param("code") String code);

    FeatureObjectCategory selectByName(@Param("name") String name);

    FeatureObjectCategory selectByCategoryId(@Param("id") Long id);

    List<FeatureObjectCategory> selectAll();
}
