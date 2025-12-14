package com.sunny.kg.exception;

/**
 * 知识库 SDK 错误码
 *
 * @author Sunny
 * @since 2025-12-10
 */
public enum KnowledgeErrorCode {

    // ==================== 连接错误 ====================
    CONNECTION_FAILED("KG_CONN_001", "Neo4j 连接失败: %s"),
    CONNECTION_TIMEOUT("KG_CONN_002", "Neo4j 连接超时"),
    CONNECTION_CLOSED("KG_CONN_003", "连接已关闭"),

    // ==================== 配置错误 ====================
    CONFIG_INVALID("KG_CFG_001", "配置无效: %s"),
    CONFIG_MISSING("KG_CFG_002", "缺少必要配置: %s"),

    // ==================== 数据错误 ====================
    DATA_INVALID("KG_DATA_001", "数据无效: %s"),
    DATA_MAPPING_FAILED("KG_DATA_002", "数据映射失败: %s"),

    // ==================== 执行错误 ====================
    EXECUTE_FAILED("KG_EXEC_001", "执行失败: %s"),
    EXECUTE_TIMEOUT("KG_EXEC_002", "执行超时"),
    RETRY_EXHAUSTED("KG_EXEC_003", "重试次数已耗尽"),

    // ==================== 系统错误 ====================
    INTERNAL_ERROR("KG_SYS_001", "内部错误: %s"),
    UNKNOWN_ERROR("KG_SYS_999", "未知错误: %s");

    private final String code;
    private final String messageTemplate;

    KnowledgeErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public String getCode() {
        return code;
    }

    public String formatMessage(Object... args) {
        return String.format(messageTemplate, args);
    }

}
