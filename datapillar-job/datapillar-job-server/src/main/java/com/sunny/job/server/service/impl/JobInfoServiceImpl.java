package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobInfo;
import com.sunny.job.server.mapper.JobInfoMapper;
import com.sunny.job.server.service.JobInfoService;
import org.springframework.stereotype.Service;

/**
 * 任务定义 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo>
        implements JobInfoService {
}
