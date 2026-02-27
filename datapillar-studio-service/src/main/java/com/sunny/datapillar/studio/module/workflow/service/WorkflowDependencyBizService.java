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
 * 工作流Dependency业务服务
 * 提供工作流Dependency业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowDependencyBizService {

    List<JobDependencyResponse> getDependenciesByWorkflowId(Long workflowId);

    List<JobDependencyResponse> getDependenciesByJobId(Long jobId);

    Long createDependency(Long workflowId, JobDependencyCreateRequest dto);

    void deleteDependency(Long workflowId, Long jobId, Long parentJobId);
}
