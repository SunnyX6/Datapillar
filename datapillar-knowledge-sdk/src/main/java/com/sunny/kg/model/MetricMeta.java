package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 指标元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class MetricMeta {

    /**
     * 指标类型
     */
    private MetricType type;

    /**
     * 指标名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 指标描述
     */
    private String description;

    /**
     * 计算公式
     */
    private String formula;

    /**
     * 公式表达式
     */
    private String formulaExpression;

    /**
     * 单位
     */
    private String unit;

    /**
     * 分类
     */
    private String category;

    /**
     * 聚合类型 (SUM/COUNT/AVG 等，原子指标用)
     */
    private String metricAggType;

    /**
     * 绑定的字段名 (原子指标用)
     */
    private String boundColumn;

    /**
     * 绑定的表名 (原子指标用)
     */
    private String boundTable;

    /**
     * 来源指标名称列表 (派生/复合指标用)
     */
    private List<String> sourceMetrics;

    /**
     * 时间修饰词 (派生指标用)
     */
    private String timeModifier;

    /**
     * 业务重要性 (HIGH/MEDIUM/LOW)
     */
    private String businessImportance;

    /**
     * 认证级别 (OFFICIAL/CERTIFIED/NONE)
     */
    private String certificationLevel;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

    /**
     * 指标类型枚举
     */
    public enum MetricType {
        ATOMIC,     // 原子指标
        DERIVED,    // 派生指标
        COMPOSITE   // 复合指标
    }

}
