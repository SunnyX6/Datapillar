package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiModelGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * AIModel authorizationMapper responsibleAIModel authorization data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-02-22
 */
@Mapper
public interface AiModelGrantMapper extends BaseMapper<AiModelGrant> {}
