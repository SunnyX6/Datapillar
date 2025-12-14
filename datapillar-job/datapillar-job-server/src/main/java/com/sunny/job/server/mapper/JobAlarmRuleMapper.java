package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobAlarmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobAlarmRuleMapper extends BaseMapper<JobAlarmRule> {
}
