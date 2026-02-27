package com.sunny.datapillar.studio.module.workflow.service.impl;

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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public IPage<WorkflowListItemResponse> getWorkflowPage(Page<WorkflowListItemResponse> page,
                                                        Long projectId,
                                                        String workflowName,
                                                        Integer status) {
        return workflowService.getWorkflowPage(page, projectId, workflowName, status);
    }

    @Override
    public WorkflowResponse getWorkflowDetail(Long id) {
        return workflowService.getWorkflowDetail(id);
    }

    @Override
    public Long createWorkflow(WorkflowCreateRequest dto) {
        return workflowService.createWorkflow(dto);
    }

    @Override
    public void updateWorkflow(Long id, WorkflowUpdateRequest dto) {
        workflowService.updateWorkflow(id, dto);
    }

    @Override
    public void deleteWorkflow(Long id) {
        workflowService.deleteWorkflow(id);
    }
}
