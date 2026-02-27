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
 * 任务服务
 * 提供任务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobService {

    /**
     * 查询工作流下的所有任务
     */
    List<JobResponse> getJobsByWorkflowId(Long workflowId);

    /**
     * 获取任务详情
     */
    JobResponse getJobDetail(Long workflowId, Long id);

    /**
     * 创建任务
     */
    Long createJob(Long workflowId, JobCreateRequest dto);

    /**
     * 更新任务
     */
    void updateJob(Long workflowId, Long id, JobUpdateRequest dto);

    /**
     * 删除任务
     */
    void deleteJob(Long workflowId, Long id);

    /**
     * 批量更新任务位置
     */
    void updateJobPositions(Long workflowId, JobLayoutSaveRequest dto);
}
