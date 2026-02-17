package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowBizService;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流业务服务实现
 * 实现工作流业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class WorkflowBizServiceImpl implements WorkflowBizService {

    private final WorkflowService workflowService;

    @Override
    public IPage<WorkflowDto.ListItem> getWorkflowPage(Page<WorkflowDto.ListItem> page,
                                                        Long projectId,
                                                        String workflowName,
                                                        Integer status) {
        return workflowService.getWorkflowPage(page, projectId, workflowName, status);
    }

    @Override
    public WorkflowDto.Response getWorkflowDetail(Long id) {
        return workflowService.getWorkflowDetail(id);
    }

    @Override
    public Long createWorkflow(WorkflowDto.Create dto) {
        return workflowService.createWorkflow(dto);
    }

    @Override
    public void updateWorkflow(Long id, WorkflowDto.Update dto) {
        workflowService.updateWorkflow(id, dto);
    }

    @Override
    public void deleteWorkflow(Long id) {
        workflowService.deleteWorkflow(id);
    }
}
