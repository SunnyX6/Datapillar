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
 * Task service Provide task business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobService {

  /** Query all tasks under the workflow */
  List<JobResponse> getJobsByWorkflowId(Long workflowId);

  /** Get task details */
  JobResponse getJobDetail(Long workflowId, Long id);

  /** Create tasks */
  Long createJob(Long workflowId, JobCreateRequest dto);

  /** update task */
  void updateJob(Long workflowId, Long id, JobUpdateRequest dto);

  /** Delete task */
  void deleteJob(Long workflowId, Long id);

  /** Update task locations in batches */
  void updateJobPositions(Long workflowId, JobLayoutSaveRequest dto);
}
