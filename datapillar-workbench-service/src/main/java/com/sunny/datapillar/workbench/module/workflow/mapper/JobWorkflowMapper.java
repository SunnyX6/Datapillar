package com.sunny.datapillar.workbench.module.workflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.workbench.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.workbench.module.workflow.entity.JobWorkflow;

/**
 * 工作流 Mapper 接口
 *
 * @author sunny
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
