package com.sunny.kg.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 优雅关闭管理器
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    /**
     * 优雅关闭（使用默认超时 30s）
     *
     * @param flushAction   刷新缓冲区动作
     * @param executors     需要关闭的线程池
     * @param closeAction   最终关闭动作
     * @return 是否在超时前完成
     */
    public boolean shutdown(Runnable flushAction, ExecutorService[] executors, Runnable closeAction) {
        return shutdown(DEFAULT_TIMEOUT, flushAction, executors, closeAction);
    }

    /**
     * 优雅关闭（指定超时时间）
     *
     * @param timeout       超时时间
     * @param flushAction   刷新缓冲区动作
     * @param executors     需要关闭的线程池
     * @param closeAction   最终关闭动作
     * @return 是否在超时前完成
     */
    public boolean shutdown(Duration timeout, Runnable flushAction, ExecutorService[] executors, Runnable closeAction) {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            log.warn("关闭已在进行中，等待完成...");
            try {
                return shutdownLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.info("开始优雅关闭，超时时间: {}s", timeout.getSeconds());
        long startTime = System.currentTimeMillis();
        long remainingMs = timeout.toMillis();
        boolean success = true;

        try {
            // 1. 刷新缓冲区
            if (flushAction != null) {
                log.debug("刷新缓冲区...");
                flushAction.run();
            }

            // 2. 关闭线程池
            if (executors != null) {
                for (ExecutorService executor : executors) {
                    if (executor != null && !executor.isShutdown()) {
                        executor.shutdown();
                    }
                }

                // 等待线程池终止
                for (ExecutorService executor : executors) {
                    if (executor != null) {
                        remainingMs = timeout.toMillis() - (System.currentTimeMillis() - startTime);
                        if (remainingMs <= 0) {
                            log.warn("关闭超时，强制终止线程池");
                            executor.shutdownNow();
                            success = false;
                        } else {
                            if (!executor.awaitTermination(remainingMs, TimeUnit.MILLISECONDS)) {
                                log.warn("线程池未能在超时前终止，强制关闭");
                                executor.shutdownNow();
                                success = false;
                            }
                        }
                    }
                }
            }

            // 3. 执行最终关闭动作
            if (closeAction != null) {
                log.debug("执行最终关闭动作...");
                closeAction.run();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("优雅关闭完成，耗时: {}ms, 成功: {}", duration, success);
            return success;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("关闭被中断");
            return false;
        } catch (Exception e) {
            log.error("关闭过程中发生异常", e);
            return false;
        } finally {
            shutdownLatch.countDown();
        }
    }

    /**
     * 强制立即关闭
     */
    public void shutdownNow(ExecutorService[] executors, Runnable closeAction) {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            return;
        }

        log.warn("强制立即关闭");
        try {
            if (executors != null) {
                for (ExecutorService executor : executors) {
                    if (executor != null) {
                        executor.shutdownNow();
                    }
                }
            }
            if (closeAction != null) {
                closeAction.run();
            }
        } finally {
            shutdownLatch.countDown();
        }
    }

    /**
     * 是否已开始关闭
     */
    public boolean isShutdownInitiated() {
        return shutdownInitiated.get();
    }

}
