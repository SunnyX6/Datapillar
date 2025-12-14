package com.sunny.job.executor.demo;

import com.sunny.job.core.handler.JobContext;
import com.sunny.job.core.handler.JobHandlerProvider;
import com.sunny.job.core.handler.JobHandlerRegistry;
import com.sunny.job.core.handler.MethodJobHandler;
import com.sunny.job.executor.demo.handler.ShellHandler;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 内置处理器 PF4J 插件扩展
 * <p>
 * 注册 SHELL、PYTHON、HTTP 等内置处理器
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Extension
public class BuiltinHandlerProvider implements JobHandlerProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltinHandlerProvider.class);

    @Override
    public void registerHandlers(JobHandlerRegistry registry) {
        log.info("注册内置处理器...");

        // 注册 SHELL 处理器
        registerShellHandler(registry);

        log.info("内置处理器注册完成");
    }

    private void registerShellHandler(JobHandlerRegistry registry) {
        try {
            ShellHandler handler = new ShellHandler();
            Method method = ShellHandler.class.getMethod("execute", JobContext.class);
            MethodJobHandler methodHandler = new MethodJobHandler(handler, method, null, null);
            registry.register("SHELL", methodHandler);
        } catch (Exception e) {
            log.error("注册 SHELL 处理器失败", e);
        }
    }
}
