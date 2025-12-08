package com.sunny.job.admin.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/**
 * Gson 工具类
 *
 * @author sunny
 * @since 2025-12-08
 */
public class GsonTool {

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * JSON 字符串转对象（泛型支持）
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    /**
     * JSON 字符串转对象（TypeToken 支持）
     */
    public static <T> T fromJson(String json, TypeToken<T> typeToken) {
        return GSON.fromJson(json, typeToken.getType());
    }

    /**
     * JSON 字符串转泛型List（支持原xxl-tool签名）
     * 例如: fromJson(json, List.class, HandleCallbackParam.class)
     */
    public static <T> T fromJson(String json, Class<?> collectionClass, Class<?> elementClass) {
        // 使用TypeToken来创建泛型类型
        Type type = TypeToken.getParameterized(collectionClass, elementClass).getType();
        return GSON.fromJson(json, type);
    }
}
