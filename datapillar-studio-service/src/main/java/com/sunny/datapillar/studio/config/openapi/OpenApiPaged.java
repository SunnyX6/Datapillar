package com.sunny.datapillar.studio.config.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Paging interface markup annotation Interface for marking pagination fields that need to be
 * preserved in the document
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenApiPaged {}
