package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobNamespace;
import com.sunny.job.server.mapper.JobNamespaceMapper;
import com.sunny.job.server.service.JobNamespaceService;
import org.springframework.stereotype.Service;

/**
 * 命名空间 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobNamespaceServiceImpl extends ServiceImpl<JobNamespaceMapper, JobNamespace>
        implements JobNamespaceService {
}
