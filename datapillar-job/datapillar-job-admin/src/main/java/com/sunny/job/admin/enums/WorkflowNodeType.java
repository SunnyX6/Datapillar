package com.sunny.job.admin.enums;

import lombok.Getter;

/**
 * Workflow节点类型枚举
 * 对应前端组件库的节点类型
 */
@Getter
public enum WorkflowNodeType {

    /**
     * 开始节点
     */
    START("start", "startJobHandler", "工作流开始节点"),

    /**
     * 结束节点
     */
    END("end", "endJobHandler", "工作流结束节点"),

    /**
     * Shell命令节点
     */
    SHELL("shell", "shellJobHandler", "Shell命令执行"),

    /**
     * JDBC数据源节点
     */
    JDBC_DATASOURCE("jdbc-datasource", "jdbcJobHandler", "JDBC数据源"),

    /**
     * DataX数据同步节点
     */
    DATAX("datax", "dataxJobHandler", "DataX数据同步"),

    /**
     * Hive数据处理节点
     */
    HIVE("hive", "hiveJobHandler", "Hive数据处理"),

    /**
     * Flink流处理节点
     */
    FLINK("flink", "flinkJobHandler", "Flink流处理"),

    /**
     * HTTP请求节点
     */
    HTTP("http", "httpJobHandler", "HTTP请求"),

    /**
     * 自定义节点
     */
    CUSTOM("custom", "customJobHandler", "自定义任务");

    /**
     * 前端节点类型标识
     */
    private final String nodeType;

    /**
     * 对应的Datapillar-Job JobHandler名称
     */
    private final String jobHandler;

    /**
     * 节点描述
     */
    private final String description;

    WorkflowNodeType(String nodeType, String jobHandler, String description) {
        this.nodeType = nodeType;
        this.jobHandler = jobHandler;
        this.description = description;
    }

    /**
     * 根据前端节点类型获取对应的JobHandler名称
     *
     * @param nodeType 前端节点类型
     * @return JobHandler名称，如果找不到则返回null
     */
    public static String getJobHandlerByNodeType(String nodeType) {
        if (nodeType == null) {
            return null;
        }

        for (WorkflowNodeType type : values()) {
            if (type.nodeType.equals(nodeType)) {
                return type.jobHandler;
            }
        }

        return null;
    }

    /**
     * 根据JobHandler名称获取节点类型
     *
     * @param jobHandler JobHandler名称
     * @return 节点类型，如果找不到则返回null
     */
    public static String getNodeTypeByJobHandler(String jobHandler) {
        if (jobHandler == null) {
            return null;
        }

        for (WorkflowNodeType type : values()) {
            if (type.jobHandler.equals(jobHandler)) {
                return type.nodeType;
            }
        }

        return null;
    }

    /**
     * 判断是否为有效的节点类型
     *
     * @param nodeType 节点类型
     * @return true if valid
     */
    public static boolean isValidNodeType(String nodeType) {
        return getJobHandlerByNodeType(nodeType) != null;
    }

    /**
     * 判断是否为有效的JobHandler
     *
     * @param jobHandler JobHandler名称
     * @return true if valid
     */
    public static boolean isValidJobHandler(String jobHandler) {
        return getNodeTypeByJobHandler(jobHandler) != null;
    }
}
