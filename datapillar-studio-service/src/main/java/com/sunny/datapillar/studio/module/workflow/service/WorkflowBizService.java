package com.sunny.datapillar.studio.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;

/**
 * 工作流业务服务
 * 提供工作流业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowBizService {

    IPage<WorkflowDto.ListItem> getWorkflowPage(Page<WorkflowDto.ListItem> page, Long projectId, String workflowName, Integer status);

    WorkflowDto.Response getWorkflowDetail(Long id);

    Long createWorkflow(WorkflowDto.Create dto);

    void updateWorkflow(Long id, WorkflowDto.Update dto);

    void deleteWorkflow(Long id);
}
