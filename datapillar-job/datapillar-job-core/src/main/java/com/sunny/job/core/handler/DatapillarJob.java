package com.sunny.job.core.handler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务处理器注解
 * <p>
 * 标记在方法上，将方法注册为任务处理器。
 * Worker 启动时自动扫描所有带此注解的方法，建立 handler 名称到方法的映射。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Component
 * public class MyJobHandler {
 *
 *     &#64;DatapillarJob("syncUserData")
 *     public void syncUserData(String params) {
 *         // 业务逻辑
 *     }
 *
 *     &#64;DatapillarJob(value = "sendEmail", timeout = 60)
 *     public void sendEmail(String params) {
 *         // 发送邮件
 *     }
 * }
 * </pre>
 * <p>
 * 方法签名要求：
 * - 返回值：void 或 String（返回执行结果消息）
 * - 参数：无参 或 (String params) 或 (JobContext context)
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DatapillarJob {

    /**
     * 任务处理器名称（必填）
     * <p>
     * 全局唯一，用于任务配置中指定执行哪个处理器
     */
    String value();

    /**
     * 初始化方法名（可选）
     * <p>
     * 在处理器第一次执行前调用，用于初始化资源
     */
    String init() default "";

    /**
     * 销毁方法名（可选）
     * <p>
     * 在 Worker 关闭时调用，用于释放资源
     */
    String destroy() default "";
}
