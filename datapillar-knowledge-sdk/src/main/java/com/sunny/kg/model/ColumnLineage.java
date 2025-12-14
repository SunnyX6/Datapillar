package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 列级血缘
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class ColumnLineage {

    /**
     * 来源表名
     */
    private String sourceTable;

    /**
     * 来源列名
     */
    private String sourceColumn;

    /**
     * 目标列名
     */
    private String targetColumn;

    /**
     * 转换类型 (DIRECT/AGGREGATE/EXPRESSION)
     */
    private String transformationType;

    /**
     * 转换函数 (SUM/COUNT/COUNT_DISTINCT/AVG 等)
     */
    private String transformationFunction;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
