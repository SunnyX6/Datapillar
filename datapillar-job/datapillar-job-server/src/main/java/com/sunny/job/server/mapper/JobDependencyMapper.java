package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务依赖关系 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobDependencyMapper extends BaseMapper<JobDependency> {

    /**
     * 查询工作流下所有依赖
     */
    List<JobDependency> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 逻辑删除工作流下所有依赖
     */
    int softDeleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除任务相关的所有依赖
     */
    int deleteByJobId(@Param("workflowId") Long workflowId, @Param("jobId") Long jobId);

    /**
     * 统计依赖数量
     */
    long countDependency(@Param("workflowId") Long workflowId,
                         @Param("jobId") Long jobId,
                         @Param("parentJobId") Long parentJobId);

    /**
     * 删除依赖
     */
    int deleteDependency(@Param("workflowId") Long workflowId,
                         @Param("jobId") Long jobId,
                         @Param("parentJobId") Long parentJobId);
}
