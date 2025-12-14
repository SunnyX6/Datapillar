package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务定义 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobInfoMapper {

    /**
     * 根据 ID 查询任务定义
     *
     * @param jobId 任务 ID
     * @return 任务定义
     */
    JobInfo selectById(@Param("jobId") Long jobId);

    /**
     * 查询工作流下所有任务定义
     *
     * @param workflowId 工作流ID
     * @return 任务定义列表
     */
    List<JobInfo> selectByWorkflowId(@Param("workflowId") Long workflowId);
}
