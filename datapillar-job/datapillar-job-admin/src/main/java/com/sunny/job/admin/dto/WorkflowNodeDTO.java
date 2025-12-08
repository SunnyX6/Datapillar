package com.sunny.job.admin.dto;

import lombok.Data;

/**
 * Workflow节点DTO
 * 前端只需要提供业务相关字段，后端自动处理技术细节
 * 前端无需关心glue、executor等datapillar-job内部概念
 *
 * @author sunny
 * @date 2025-11-10
 */
@Data
public class WorkflowNodeDTO {

    /**
     * 节点ID（前端生成的唯一标识）
     */
    private String nodeId;

    /**
     * 节点类型（对应前端NodeType）
     * 例如: shell, jdbc-datasource, datax, hive, flink, python等
     * 后端会根据此字段自动映射到对应的JobHandler
     */
    private String nodeType;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点描述（可选）
     */
    private String description;

    /**
     * 节点执行内容
     * 不同类型节点存储不同的执行内容：
     * - shell: shell脚本内容
     * - jdbc: SQL语句
     * - hive: HiveQL语句
     * - python: Python代码
     * - flink: Flink SQL或作业配置
     *
     * 后端映射: content -> glue_source (前端无需知道glue概念)
     */
    private String content;

    /**
     * 节点配置参数（JSON字符串）
     * 存储执行时需要的配置参数，例如：
     * - jdbc: {"datasourceId": "1", "timeout": 30}
     * - datax: {"readerConfig": {...}, "writerConfig": {...}}
     * - hive: {"database": "default", "queue": "default"}
     *
     * 后端映射: config -> executor_param
     */
    private String config;

    /**
     * 报警邮件（可选）
     */
    private String alarmEmail;

    /**
     * 任务执行超时时间（秒，可选，默认0表示不超时）
     */
    private Integer executorTimeout;

    /**
     * 失败重试次数（可选，默认0表示不重试）
     */
    private Integer executorFailRetryCount;

    /**
     * 创建者ID（可选，批量添加时由前端提供以避免数据库查询）
     */
    private Long createdBy;
}
