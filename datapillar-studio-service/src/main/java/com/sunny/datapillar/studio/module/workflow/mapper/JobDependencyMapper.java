package com.sunny.datapillar.studio.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
import com.sunny.datapillar.studio.module.workflow.entity.JobDependency;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TaskDependencyMapper Responsible for tasksDependencyData access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobDependencyMapper extends BaseMapper<JobDependency> {

  /** Query all dependencies under the workflow */
  List<JobDependencyResponse> selectByWorkflowId(@Param("workflowId") Long workflowId);

  /** Query the upstream dependencies of a task */
  List<JobDependencyResponse> selectByJobId(@Param("jobId") Long jobId);

  /** Remove specified dependencies */
  int deleteDependency(
      @Param("workflowId") Long workflowId,
      @Param("jobId") Long jobId,
      @Param("parentJobId") Long parentJobId);

  /** According to workflowIDRemove all dependencies（tombstone） */
  int deleteByWorkflowId(@Param("workflowId") Long workflowId);

  /** Remove all dependencies related to the task（tombstone） */
  int deleteByJobId(@Param("jobId") Long jobId);

  /** Check if dependencies exist */
  int existsDependency(@Param("jobId") Long jobId, @Param("parentJobId") Long parentJobId);
}
