package com.sunny.kg.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 拦截器链
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class InterceptorChain {

    private static final Logger log = LoggerFactory.getLogger(InterceptorChain.class);

    private final List<Interceptor> interceptors;

    public InterceptorChain() {
        this.interceptors = new ArrayList<>();
        loadFromSpi();
    }

    /**
     * 通过 SPI 加载拦截器
     */
    private void loadFromSpi() {
        ServiceLoader<Interceptor> loader = ServiceLoader.load(Interceptor.class);
        for (Interceptor interceptor : loader) {
            interceptors.add(interceptor);
            log.info("加载拦截器: {}", interceptor.getClass().getName());
        }
        interceptors.sort(Comparator.comparingInt(Interceptor::order));
    }

    /**
     * 手动添加拦截器
     */
    public void addInterceptor(Interceptor interceptor) {
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt(Interceptor::order));
    }

    /**
     * 执行前置拦截
     *
     * @return true 继续执行，false 中断
     */
    public boolean applyBefore(InterceptorContext context) {
        for (Interceptor interceptor : interceptors) {
            try {
                if (!interceptor.beforeEmit(context)) {
                    log.debug("拦截器 {} 中断执行", interceptor.getClass().getSimpleName());
                    return false;
                }
            } catch (Exception e) {
                log.warn("拦截器 {} beforeEmit 异常: {}",
                    interceptor.getClass().getSimpleName(), e.getMessage());
            }
        }
        return true;
    }

    /**
     * 执行后置拦截
     */
    public void applyAfter(InterceptorContext context) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).afterEmit(context);
            } catch (Exception e) {
                log.warn("拦截器 {} afterEmit 异常: {}",
                    interceptors.get(i).getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 执行异常拦截
     */
    public void applyError(InterceptorContext context, Exception exception) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).onError(context, exception);
            } catch (Exception e) {
                log.warn("拦截器 {} onError 异常: {}",
                    interceptors.get(i).getClass().getSimpleName(), e.getMessage());
            }
        }
    }

}
