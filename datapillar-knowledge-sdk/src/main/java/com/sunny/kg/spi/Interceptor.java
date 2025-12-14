package com.sunny.kg.spi;

/**
 * 拦截器接口
 * <p>
 * 允许在数据写入前后执行自定义逻辑
 *
 * @author Sunny
 * @since 2025-12-10
 */
public interface Interceptor {

    /**
     * 写入前拦截
     *
     * @param context 拦截上下文
     * @return true 继续执行，false 中断执行
     */
    default boolean beforeEmit(InterceptorContext context) {
        return true;
    }

    /**
     * 写入后拦截
     *
     * @param context 拦截上下文
     */
    default void afterEmit(InterceptorContext context) {
    }

    /**
     * 写入异常时拦截
     *
     * @param context   拦截上下文
     * @param exception 异常
     */
    default void onError(InterceptorContext context, Exception exception) {
    }

    /**
     * 拦截器优先级（数值越小优先级越高）
     */
    default int order() {
        return 0;
    }

}
