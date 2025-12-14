package com.sunny.job.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.job.server.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务定义 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {
}
