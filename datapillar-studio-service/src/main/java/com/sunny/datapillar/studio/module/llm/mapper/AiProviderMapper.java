package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI提供器Mapper
 * 负责AI提供器数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {
}
