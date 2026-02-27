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
 * 任务Dependency服务
 * 提供任务Dependency业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobDependencyService {

    /**
     * 查询工作流下的所有依赖
     */
    List<JobDependencyResponse> getDependenciesByWorkflowId(Long workflowId);

    /**
     * 查询任务的上游依赖
     */
    List<JobDependencyResponse> getDependenciesByJobId(Long jobId);

    /**
     * 创建依赖关系
     */
    Long createDependency(Long workflowId, JobDependencyCreateRequest dto);

    /**
     * 删除依赖关系
     */
    void deleteDependency(Long workflowId, Long jobId, Long parentJobId);
}
