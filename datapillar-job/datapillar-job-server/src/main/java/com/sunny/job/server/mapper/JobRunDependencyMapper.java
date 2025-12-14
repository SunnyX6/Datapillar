package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobRunDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行依赖关系 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobRunDependencyMapper extends BaseMapper<JobRunDependency> {

    /**
     * 查询任务的所有上游依赖
     *
     * @param jobRunId 任务执行实例ID
     * @return 上游任务执行实例ID列表
     */
    List<Long> selectParentRunIds(@Param("jobRunId") Long jobRunId);

    /**
     * 查询任务的所有下游依赖
     *
     * @param parentRunId 上游任务执行实例ID
     * @return 下游任务执行实例ID列表
     */
    List<Long> selectChildRunIds(@Param("parentRunId") Long parentRunId);
}
