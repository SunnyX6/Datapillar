package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobWorkflowRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行实例 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowRunMapper extends BaseMapper<JobWorkflowRun> {
}
