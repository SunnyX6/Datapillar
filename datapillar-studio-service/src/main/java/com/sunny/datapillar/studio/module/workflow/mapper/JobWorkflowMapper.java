package com.sunny.datapillar.studio.module.workflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;

/**
 * 任务工作流Mapper
 * 负责任务工作流数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobWorkflowMapper extends BaseMapper<JobWorkflow> {

    /**
     * 分页查询工作流列表
     */
    IPage<WorkflowDto.ListItem> selectWorkflowPage(
            Page<WorkflowDto.ListItem> page,
            @Param("projectId") Long projectId,
            @Param("workflowName") String workflowName,
            @Param("status") Integer status
    );

    /**
     * 查询工作流详情（含项目信息）
     */
    WorkflowDto.Response selectWorkflowDetail(@Param("id") Long id);

    /**
     * 根据项目查询工作流列表
     */
    List<JobWorkflow> selectByProjectId(@Param("projectId") Long projectId);
}
