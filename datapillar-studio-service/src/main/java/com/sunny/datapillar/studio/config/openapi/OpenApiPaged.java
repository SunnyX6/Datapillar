package com.sunny.datapillar.studio.config.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分页接口标记注解
 * 用于标记需要在文档中保留分页字段的接口
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenApiPaged {
}
