package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobAlarmChannel;
import com.sunny.job.server.mapper.JobAlarmChannelMapper;
import com.sunny.job.server.service.JobAlarmChannelService;
import org.springframework.stereotype.Service;

/**
 * 告警渠道 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobAlarmChannelServiceImpl extends ServiceImpl<JobAlarmChannelMapper, JobAlarmChannel>
        implements JobAlarmChannelService {
}
