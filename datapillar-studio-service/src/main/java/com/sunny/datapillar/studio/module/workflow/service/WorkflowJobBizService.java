package com.sunny.datapillar.studio.module.workflow.service;

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

/**
 * 工作流任务业务服务
 * 提供工作流任务业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowJobBizService {

    List<JobResponse> getJobsByWorkflowId(Long workflowId);

    JobResponse getJobDetail(Long workflowId, Long id);

    Long createJob(Long workflowId, JobCreateRequest dto);

    void updateJob(Long workflowId, Long id, JobUpdateRequest dto);

    void deleteJob(Long workflowId, Long id);

    void updateJobPositions(Long workflowId, JobLayoutSaveRequest dto);
}
