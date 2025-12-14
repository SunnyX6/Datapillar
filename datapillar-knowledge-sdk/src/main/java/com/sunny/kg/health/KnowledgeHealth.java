package com.sunny.kg.health;

import java.util.Map;

/**
 * 知识库客户端健康检查接口
 * <p>
 * SDK 只提供健康状态数据，使用方自行决定如何暴露（Actuator/K8s Probe 等）
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface KnowledgeHealth {

    /**
     * 健康状态枚举
     */
    enum Status {
        UP,       // 正常
        DOWN,     // 不可用
        DEGRADED  // 降级（部分功能可用）
    }

    /**
     * 是否健康
     */
    boolean isHealthy();

    /**
     * 获取健康状态
     */
    Status getStatus();

    /**
     * 获取详细信息
     */
    Map<String, Object> getDetails();

    /**
     * 执行健康检查（主动检测）
     */
    HealthCheckResult check();

}
