package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobWorkflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流定义 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowMapper extends BaseMapper<JobWorkflow> {
}
