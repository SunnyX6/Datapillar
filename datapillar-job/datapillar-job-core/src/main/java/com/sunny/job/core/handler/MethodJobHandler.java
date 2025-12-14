package com.sunny.job.core.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 方法级任务处理器
 * <p>
 * 封装 @DatapillarJob 标注的方法，提供执行能力
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class MethodJobHandler {

    private static final Logger log = LoggerFactory.getLogger(MethodJobHandler.class);

    private final Object target;
    private final Method method;
    private final Method initMethod;
    private final Method destroyMethod;

    private volatile boolean initialized = false;

    public MethodJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;

        // 确保可访问
        this.method.setAccessible(true);
        if (this.initMethod != null) {
            this.initMethod.setAccessible(true);
        }
        if (this.destroyMethod != null) {
            this.destroyMethod.setAccessible(true);
        }
    }

    /**
     * 执行任务
     *
     * @param context 任务上下文
     */
    public void execute(JobContext context) throws Exception {
        // 首次执行时调用 init 方法
        if (!initialized && initMethod != null) {
            synchronized (this) {
                if (!initialized) {
                    log.debug("执行 init 方法: {}", initMethod.getName());
                    initMethod.invoke(target);
                    initialized = true;
                }
            }
        }

        // 设置线程上下文
        JobContext.set(context);

        try {
            Object result = invokeMethod(context);

            // 处理返回值
            if (result != null) {
                if (result instanceof String msg) {
                    context.setSuccess(msg);
                } else if (result instanceof Boolean success) {
                    if (success) {
                        context.setSuccess();
                    } else {
                        context.setFail("执行返回 false");
                    }
                }
            }
        } finally {
            JobContext.clear();
        }
    }

    /**
     * 根据方法签名调用目标方法
     */
    private Object invokeMethod(JobContext context) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();

        try {
            if (paramTypes.length == 0) {
                // 无参方法
                return method.invoke(target);
            } else if (paramTypes[0] == String.class) {
                // (String params) 参数
                return method.invoke(target, context.getParams());
            } else if (paramTypes[0] == JobContext.class) {
                // (JobContext context) 参数
                return method.invoke(target, context);
            } else {
                throw new IllegalStateException("不支持的方法签名: " + method);
            }
        } catch (InvocationTargetException e) {
            // 解包反射异常，抛出原始异常
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * 销毁处理器
     */
    public void destroy() {
        if (destroyMethod != null) {
            try {
                log.debug("执行 destroy 方法: {}", destroyMethod.getName());
                destroyMethod.invoke(target);
            } catch (Exception e) {
                log.error("destroy 方法执行失败", e);
            }
        }
    }

    /**
     * 获取处理器描述
     */
    public String getDescription() {
        return target.getClass().getSimpleName() + "." + method.getName();
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
