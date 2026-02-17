package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AIUsageMapper
 * 负责AIUsage数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface AiUsageMapper extends BaseMapper<AiUsage> {
}
