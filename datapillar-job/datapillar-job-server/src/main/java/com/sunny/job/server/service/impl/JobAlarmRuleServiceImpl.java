package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.server.entity.JobAlarmRule;
import com.sunny.job.server.mapper.JobAlarmRuleMapper;
import com.sunny.job.server.service.JobAlarmRuleService;
import org.springframework.stereotype.Service;

/**
 * 告警规则 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobAlarmRuleServiceImpl extends ServiceImpl<JobAlarmRuleMapper, JobAlarmRule>
        implements JobAlarmRuleService {
}
