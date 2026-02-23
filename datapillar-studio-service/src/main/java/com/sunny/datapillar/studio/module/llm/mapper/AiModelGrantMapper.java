package com.sunny.datapillar.studio.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.llm.entity.AiModelGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI模型授权Mapper
 * 负责AI模型授权数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-02-22
 */
@Mapper
public interface AiModelGrantMapper extends BaseMapper<AiModelGrant> {
}
