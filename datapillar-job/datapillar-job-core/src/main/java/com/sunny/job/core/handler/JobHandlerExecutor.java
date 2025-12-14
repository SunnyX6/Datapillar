package com.sunny.job.core.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 任务处理器执行器
 * <p>
 * 负责执行具体的任务处理器，支持超时控制
 * <p>
 * 处理器匹配逻辑：
 * 直接根据 jobType 查找对应的处理器（@DatapillarJob("SHELL") 匹配 jobType=SHELL）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component
public class JobHandlerExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobHandlerExecutor.class);

    private final JobHandlerRegistry handlerRegistry;
    private final ExecutorService executorService;

    public JobHandlerExecutor(JobHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 执行任务
     *
     * @param context        任务上下文
     * @param timeoutSeconds 超时时间（秒），0 表示不限制
     * @return 执行结果
     */
    public ExecutionResult execute(JobContext context, int timeoutSeconds) {
        String jobType = context.getJobType();
        MethodJobHandler handler = handlerRegistry.getHandler(jobType);

        if (handler == null) {
            String msg = String.format("Handler 不存在: jobType=%s", jobType);
            log.warn(msg);
            return ExecutionResult.failure(msg);
        }

        log.info("开始执行任务: jobType={}, instanceId={}", jobType, context.getInstanceId());
        long startTime = System.currentTimeMillis();

        try {
            JobContext.set(context);

            if (timeoutSeconds > 0) {
                return executeWithTimeout(handler, context, timeoutSeconds);
            } else {
                return executeDirectly(handler, context);
            }
        } finally {
            JobContext.clear();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("任务执行完成: jobType={}, instanceId={}, cost={}ms, success={}",
                    jobType, context.getInstanceId(), costTime, context.isSuccess());
        }
    }

    /**
     * 直接执行（无超时）
     */
    private ExecutionResult executeDirectly(MethodJobHandler handler, JobContext context) {
        try {
            handler.execute(context);
            return context.isSuccess()
                    ? ExecutionResult.success(context.getHandleMsg())
                    : ExecutionResult.failure(context.getHandleMsg());
        } catch (Exception e) {
            log.error("任务执行异常: jobType={}", context.getJobType(), e);
            return ExecutionResult.failure("执行异常: " + e.getMessage());
        }
    }

    /**
     * 带超时执行
     */
    private ExecutionResult executeWithTimeout(MethodJobHandler handler, JobContext context, int timeoutSeconds) {
        Callable<Void> task = () -> {
            handler.execute(context);
            return null;
        };

        Future<Void> future = executorService.submit(task);

        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
            return context.isSuccess()
                    ? ExecutionResult.success(context.getHandleMsg())
                    : ExecutionResult.failure(context.getHandleMsg());
        } catch (TimeoutException e) {
            future.cancel(true);
            String msg = "执行超时: " + timeoutSeconds + "秒";
            log.warn("任务执行超时: jobType={}, timeout={}s", context.getJobType(), timeoutSeconds);
            return ExecutionResult.timeout(msg);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("任务执行异常: jobType={}", context.getJobType(), cause);
            return ExecutionResult.failure("执行异常: " + cause.getMessage());
        }
    }

    /**
     * 执行结果
     */
    public record ExecutionResult(boolean success, boolean timeout, String message) {

        public static ExecutionResult success(String message) {
            return new ExecutionResult(true, false, message != null ? message : "执行成功");
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(false, false, message);
        }

        public static ExecutionResult timeout(String message) {
            return new ExecutionResult(false, true, message);
        }
    }
}
