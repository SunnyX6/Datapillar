package com.sunny.job.server.common;

import org.springframework.scheduling.support.CronExpression;

/**
 * 参数校验工具类
 * <p>
 * 校验失败时抛出 IllegalArgumentException，由 GlobalExceptionHandler 统一处理
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /**
     * 校验字符串非空
     *
     * @param value     待校验值
     * @param fieldName 字段名称（用于错误提示）
     */
    public static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    /**
     * 校验对象非空
     *
     * @param value     待校验值
     * @param fieldName 字段名称（用于错误提示）
     */
    public static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    /**
     * 校验整数在指定范围内
     *
     * @param value     待校验值
     * @param min       最小值（包含）
     * @param max       最大值（包含）
     * @param fieldName 字段名称（用于错误提示）
     */
    public static void requireInRange(Integer value, int min, int max, String fieldName) {
        requireNotNull(value, fieldName);
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    /**
     * 校验 CRON 表达式合法性
     *
     * @param cron      CRON 表达式
     * @param fieldName 字段名称（用于错误提示）
     */
    public static void requireValidCron(String cron, String fieldName) {
        requireNotBlank(cron, fieldName);
        if (!CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException(fieldName + " 不是有效的 CRON 表达式: " + cron);
        }
    }

    /**
     * 校验正整数
     *
     * @param value     待校验值
     * @param fieldName 字段名称（用于错误提示）
     */
    public static void requirePositive(Long value, String fieldName) {
        requireNotNull(value, fieldName);
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }
}
