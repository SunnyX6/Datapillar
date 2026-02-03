package com.sunny.datapillar.studio.module.workflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.workflow.entity.JobComponent;

/**
 * 组件 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface JobComponentMapper extends BaseMapper<JobComponent> {

    /**
     * 查询所有可用组件
     */
    List<JobComponent> selectAllComponents();

    /**
     * 根据 code 查询组件
     */
    JobComponent selectByCode(@Param("code") String code);

    /**
     * 根据类型查询组件
     */
    List<JobComponent> selectByType(@Param("type") String type);
}
