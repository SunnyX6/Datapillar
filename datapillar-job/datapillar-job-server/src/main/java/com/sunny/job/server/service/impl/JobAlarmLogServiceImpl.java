package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobAlarmLog;
import com.sunny.job.server.mapper.JobAlarmLogMapper;
import com.sunny.job.server.service.JobAlarmLogService;
import org.springframework.stereotype.Service;

/**
 * 告警记录 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobAlarmLogServiceImpl extends ServiceImpl<JobAlarmLogMapper, JobAlarmLog>
        implements JobAlarmLogService {
}
