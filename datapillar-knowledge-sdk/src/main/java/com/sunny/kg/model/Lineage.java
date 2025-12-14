package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表级血缘
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class Lineage {

    /**
     * 来源表名
     */
    private String sourceTable;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * 转换类型 (SYNC/CLEAN/AGGREGATE/TRANSFORM)
     */
    private String transformationType;

    /**
     * 列级血缘列表
     */
    private List<ColumnLineage> columnLineages;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
