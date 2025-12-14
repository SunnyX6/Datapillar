package com.sunny.job.core.handler;

import org.pf4j.ExtensionPoint;

/**
 * 任务处理器提供者 PF4J 扩展点接口
 * <p>
 * 通过 PF4J 插件机制自动发现并加载处理器实现
 * <p>
 * 使用方式：
 * 1. 实现此接口并添加 @Extension 注解
 * 2. 在插件 jar 的 MANIFEST.MF 中配置 Plugin-Id、Plugin-Version
 * 3. 将 jar 放到 plugins 目录，启动时自动加载
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public interface JobHandlerProvider extends ExtensionPoint {

    /**
     * 注册处理器
     * <p>
     * 框架启动时调用，实现者在此方法中注册自己的处理器
     *
     * @param registry 处理器注册表
     */
    void registerHandlers(JobHandlerRegistry registry);
}
