package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobRun;
import com.sunny.job.server.mapper.JobRunMapper;
import com.sunny.job.server.service.JobRunService;
import org.springframework.stereotype.Service;

/**
 * 任务执行实例 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobRunServiceImpl extends ServiceImpl<JobRunMapper, JobRun>
        implements JobRunService {
}
