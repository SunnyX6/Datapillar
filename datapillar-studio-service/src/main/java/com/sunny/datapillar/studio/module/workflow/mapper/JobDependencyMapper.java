package com.sunny.datapillar.studio.module.workflow.mapper;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.workflow.entity.JobDependency;

/**
 * 任务DependencyMapper
 * 负责任务Dependency数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobDependencyMapper extends BaseMapper<JobDependency> {

    /**
     * 查询工作流下的所有依赖关系
     */
    List<JobDependencyResponse> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询任务的上游依赖
     */
    List<JobDependencyResponse> selectByJobId(@Param("jobId") Long jobId);

    /**
     * 删除指定的依赖关系
     */
    int deleteDependency(@Param("workflowId") Long workflowId,
                         @Param("jobId") Long jobId,
                         @Param("parentJobId") Long parentJobId);

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
