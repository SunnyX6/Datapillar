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
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TaskInfoMapper Responsible for tasksInfoData access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

  /** Query all tasks under the workflow（Contains component information） */
  List<JobResponse> selectJobsByWorkflowId(@Param("workflowId") Long workflowId);

  /** Query the details of a single task（Contains component information） */
  JobResponse selectJobDetail(@Param("id") Long id);

  /** Update task locations in batches */
  int batchUpdatePositions(@Param("positions") List<JobPositionItem> positions);

  /** According to workflowIDDelete all tasks（tombstone） */
  int deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
