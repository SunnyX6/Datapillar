package com.sunny.datapillar.studio.module.workflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;

/**
 * 任务 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {

    /**
     * 查询工作流下的所有任务（含组件信息）
     */
    List<JobDto.Response> selectJobsByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询单个任务详情（含组件信息）
     */
    JobDto.Response selectJobDetail(@Param("id") Long id);

    /**
     * 批量更新任务位置
     */
    int batchUpdatePositions(@Param("positions") List<JobDto.Position> positions);

    /**
     * 根据工作流ID删除所有任务（逻辑删除）
     */
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
