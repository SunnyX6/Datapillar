package com.sunny.datapillar.studio.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Task workflowMapper Responsible for task workflow data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobWorkflowMapper extends BaseMapper<JobWorkflow> {

  /** Paginated query workflow list */
  IPage<WorkflowListItemResponse> selectWorkflowPage(
      Page<WorkflowListItemResponse> page,
      @Param("projectId") Long projectId,
      @Param("workflowName") String workflowName,
      @Param("status") Integer status);

  /** Query workflow details（Contains project information） */
  WorkflowResponse selectWorkflowDetail(@Param("id") Long id);

  /** Query workflow list based on project */
  List<JobWorkflow> selectByProjectId(@Param("projectId") Long projectId);
}
