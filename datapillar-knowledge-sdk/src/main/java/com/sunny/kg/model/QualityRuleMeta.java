package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 质量规则元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class QualityRuleMeta {

    /**
     * 规则名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 规则类型 (NOT_NULL/RANGE/REGEX/UNIQUE/REFERENCE 等)
     */
    private String ruleType;

    /**
     * SQL 表达式
     */
    private String expression;

    /**
     * 是否必需
     */
    @Builder.Default
    private Boolean required = false;

    /**
     * 严重级别 (CRITICAL/HIGH/MEDIUM/LOW)
     */
    private String severity;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 绑定的字段名
     */
    private String boundColumn;

    /**
     * 绑定的表名
     */
    private String boundTable;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
