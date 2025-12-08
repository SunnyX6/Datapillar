package com.sunny.job.admin.dto;

import lombok.Data;

/**
 * IDE调试执行请求DTO
 *
 * @author sunny
 * @since 2025-12-08
 */
@Data
public class DebugExecutionRequestDTO {

    /**
     * 语言类型
     * 支持: shell, python, javascript, mysql, postgresql, hivesql, flinksql, sparksql, trino, impala
     */
    private String language;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 执行参数（可选）
     */
    private String params;

    /**
     * 超时时间（秒，默认60秒）
     */
    private Integer timeout = 60;

    /**
     * Executor地址（可选，不填则自动选择）
     */
    private String executorAddress;

    /**
     * Executor分组ID（可选，默认使用第一个分组）
     */
    private Integer executorGroupId;
}
