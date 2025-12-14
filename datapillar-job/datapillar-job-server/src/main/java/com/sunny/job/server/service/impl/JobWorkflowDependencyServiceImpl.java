package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobWorkflowDependency;
import com.sunny.job.server.mapper.JobWorkflowDependencyMapper;
import com.sunny.job.server.service.JobWorkflowDependencyService;
import org.springframework.stereotype.Service;

/**
 * 跨工作流依赖 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobWorkflowDependencyServiceImpl extends ServiceImpl<JobWorkflowDependencyMapper, JobWorkflowDependency>
        implements JobWorkflowDependencyService {
}
