package com.sunny.job.admin.util;

import java.util.Collection;

/**
 * 集合工具类
 *
 * @author sunny
 * @since 2025-12-08
 */
public class CollectionTool {

    /**
     * 判断集合是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否不为空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }
}
