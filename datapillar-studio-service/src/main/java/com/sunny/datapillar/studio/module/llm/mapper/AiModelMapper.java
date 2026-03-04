package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AIModelMapper responsibleAIModelData access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {}
