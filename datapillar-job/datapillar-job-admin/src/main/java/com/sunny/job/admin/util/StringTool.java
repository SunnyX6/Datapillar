package com.sunny.job.admin.util;

/**
 * 字符串工具类
 *
 * @author sunny
 * @since 2025-12-08
 */
public class StringTool {

    /**
     * 判断字符串是否为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 安全的 trim
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }
}
