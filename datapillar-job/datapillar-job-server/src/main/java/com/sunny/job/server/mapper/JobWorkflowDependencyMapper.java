package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobWorkflowDependency;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨工作流依赖 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowDependencyMapper extends BaseMapper<JobWorkflowDependency> {
}
