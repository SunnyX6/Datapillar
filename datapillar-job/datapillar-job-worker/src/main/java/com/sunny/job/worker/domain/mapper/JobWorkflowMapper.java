package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工作流定义 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowMapper {

    /**
     * 根据ID查询工作流定义
     *
     * @param workflowId 工作流ID
     * @return 工作流定义
     */
    JobWorkflow selectById(@Param("workflowId") Long workflowId);
}
