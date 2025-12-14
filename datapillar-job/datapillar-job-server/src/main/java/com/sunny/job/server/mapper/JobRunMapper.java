package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行实例 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobRunMapper extends BaseMapper<JobRun> {

    /**
     * 查询待执行的任务（status = WAITING）
     *
     * @return 待执行任务列表
     */
    List<JobRun> selectWaitingJobs();

    /**
     * 查询新增的任务（id > lastMaxId AND status = WAITING）
     *
     * @param lastMaxId 上次最大ID
     * @return 新增任务列表
     */
    List<JobRun> selectNewJobs(@Param("lastMaxId") Long lastMaxId);

    /**
     * 查询工作流下所有任务的状态
     *
     * @param workflowRunId 工作流执行实例ID
     * @return 任务列表
     */
    List<JobRun> selectByWorkflowRunId(@Param("workflowRunId") Long workflowRunId);
}
