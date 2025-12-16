package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobComponent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务组件 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
@Mapper
public interface JobComponentMapper {

    /**
     * 查询所有启用的组件
     *
     * @return 组件列表
     */
    List<JobComponent> selectAllEnabled();
}
