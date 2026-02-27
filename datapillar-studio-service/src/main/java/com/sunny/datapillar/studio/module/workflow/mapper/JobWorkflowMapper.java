package com.sunny.datapillar.studio.module.workflow.mapper;

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

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;

/**
 * 任务工作流Mapper
 * 负责任务工作流数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobWorkflowMapper extends BaseMapper<JobWorkflow> {

    /**
     * 分页查询工作流列表
     */
    IPage<WorkflowListItemResponse> selectWorkflowPage(
            Page<WorkflowListItemResponse> page,
            @Param("projectId") Long projectId,
            @Param("workflowName") String workflowName,
            @Param("status") Integer status
    );

    /**
     * 查询工作流详情（含项目信息）
     */
    WorkflowResponse selectWorkflowDetail(@Param("id") Long id);

    /**
     * 根据项目查询工作流列表
     */
    List<JobWorkflow> selectByProjectId(@Param("projectId") Long projectId);
}
