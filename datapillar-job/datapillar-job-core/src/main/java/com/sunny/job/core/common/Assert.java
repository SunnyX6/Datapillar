package com.sunny.job.core.common;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class Assert {

    private Assert() {
    }

    /**
     * 断言表达式为 true
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言表达式为 false
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言对象不为 null
     */
    public static <T> T notNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }

    /**
     * 断言字符串不为空
     */
    public static String notEmpty(String text, String message) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    /**
     * 断言字符串不为空白
     */
    public static String notBlank(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    /**
     * 断言集合不为空
     */
    public static <T extends Collection<?>> T notEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 断言 Map 不为空
     */
    public static <T extends Map<?, ?>> T notEmpty(T map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    /**
     * 断言数值大于 0
     */
    public static long positive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 断言数值大于等于 0
     */
    public static long notNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 断言数值在范围内 [min, max]
     */
    public static int inRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 断言状态
     */
    public static void state(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }
}
