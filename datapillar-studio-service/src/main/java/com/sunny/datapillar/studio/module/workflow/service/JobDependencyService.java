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
 * TaskDependencyservice Provide tasksDependencyBusiness capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobDependencyService {

  /** Query all dependencies under the workflow */
  List<JobDependencyResponse> getDependenciesByWorkflowId(Long workflowId);

  /** Query the upstream dependencies of a task */
  List<JobDependencyResponse> getDependenciesByJobId(Long jobId);

  /** Create dependencies */
  Long createDependency(Long workflowId, JobDependencyCreateRequest dto);

  /** Remove dependencies */
  void deleteDependency(Long workflowId, Long jobId, Long parentJobId);
}
