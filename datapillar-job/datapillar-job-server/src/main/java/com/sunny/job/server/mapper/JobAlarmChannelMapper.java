package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobAlarmChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警渠道 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobAlarmChannelMapper extends BaseMapper<JobAlarmChannel> {
}
