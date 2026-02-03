package com.sunny.datapillar.workbench.module.workflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.workbench.module.workflow.dto.JobDependencyDto;
import com.sunny.datapillar.workbench.module.workflow.entity.JobDependency;

/**
 * 任务依赖 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface JobDependencyMapper extends BaseMapper<JobDependency> {

    /**
     * 查询工作流下的所有依赖关系
     */
    List<JobDependencyDto.Response> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询任务的上游依赖
     */
    List<JobDependencyDto.Response> selectByJobId(@Param("jobId") Long jobId);

    /**
     * 删除指定的依赖关系
     */
    int deleteDependency(@Param("jobId") Long jobId, @Param("parentJobId") Long parentJobId);

    /**
     * 根据工作流ID删除所有依赖（逻辑删除）
     */
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除任务相关的所有依赖（逻辑删除）
     */
    int deleteByJobId(@Param("jobId") Long jobId);

    /**
     * 检查依赖是否存在
     */
    int existsDependency(@Param("jobId") Long jobId, @Param("parentJobId") Long parentJobId);
}
