package com.sunny.kg.validation;

import lombok.Builder;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * 数据校验配置
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Getter
@Builder
public class ValidationConfig {

    /**
     * 是否启用校验
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 表名正则（默认：字母开头，允许字母数字下划线点）
     */
    @Builder.Default
    private Pattern tableNamePattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]*$");

    /**
     * 列名正则
     */
    @Builder.Default
    private Pattern columnNamePattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 最大列数
     */
    @Builder.Default
    private int maxColumnCount = 500;

    /**
     * 最大表名长度
     */
    @Builder.Default
    private int maxTableNameLength = 128;

    /**
     * 最大列名长度
     */
    @Builder.Default
    private int maxColumnNameLength = 64;

    /**
     * 是否允许空列
     */
    @Builder.Default
    private boolean allowEmptyColumns = false;

    public static ValidationConfig defaultConfig() {
        return ValidationConfig.builder().build();
    }

    public static ValidationConfig disabled() {
        return ValidationConfig.builder().enabled(false).build();
    }

}
