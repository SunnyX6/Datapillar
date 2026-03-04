package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * AIproviderMapper responsibleAIProvider data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {}
