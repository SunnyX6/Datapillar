package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务依赖关系 Mapper（设计阶段）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobDependencyMapper extends BaseMapper<JobDependency> {

    /**
     * 查询任务的所有上游依赖
     *
     * @param jobId 任务ID
     * @return 上游任务ID列表
     */
    List<Long> selectParentJobIds(@Param("jobId") Long jobId);

    /**
     * 查询任务的所有下游依赖
     *
     * @param jobId 任务ID
     * @return 下游任务ID列表
     */
    List<Long> selectChildJobIds(@Param("jobId") Long jobId);
}
