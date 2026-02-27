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
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;

/**
 * 任务InfoMapper
 * 负责任务Info数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    /**
     * 查询工作流下的所有任务（含组件信息）
     */
    List<JobResponse> selectJobsByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询单个任务详情（含组件信息）
     */
    JobResponse selectJobDetail(@Param("id") Long id);

    /**
     * 批量更新任务位置
     */
    int batchUpdatePositions(@Param("positions") List<JobPositionItem> positions);

    /**
     * 根据工作流ID删除所有任务（逻辑删除）
     */
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
