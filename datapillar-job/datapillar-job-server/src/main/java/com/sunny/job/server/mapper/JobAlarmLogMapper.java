package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobAlarmLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警记录 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobAlarmLogMapper extends BaseMapper<JobAlarmLog> {
}
