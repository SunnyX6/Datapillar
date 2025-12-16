package com.sunny.job.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 缓存配置
 * <p>
 * 集中管理各个本地缓存的 Caffeine Cache 参数
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Configuration
@ConfigurationProperties(prefix = "datapillar.job.worker.cache")
public class CacheConfig {

    /**
     * JobRunState 缓存配置
     */
    private JobRunLocalCacheConfig jobRunState = new JobRunLocalCacheConfig();

    /**
     * SplitLocalCache 缓存配置
     */
    private SplitLocalCacheConfig splitState = new SplitLocalCacheConfig();

    /**
     * WorkerState 缓存配置
     */
    private WorkerStateCacheConfig workerState = new WorkerStateCacheConfig();

    public JobRunLocalCacheConfig getJobRunState() {
        return jobRunState;
    }

    public void setJobRunState(JobRunLocalCacheConfig jobRunState) {
        this.jobRunState = jobRunState;
    }

    public SplitLocalCacheConfig getSplitState() {
        return splitState;
    }

    public void setSplitState(SplitLocalCacheConfig splitState) {
        this.splitState = splitState;
    }

    public WorkerStateCacheConfig getWorkerState() {
        return workerState;
    }

    public void setWorkerState(WorkerStateCacheConfig workerState) {
        this.workerState = workerState;
    }

    /**
     * JobRunState 缓存配置
     */
    public static class JobRunLocalCacheConfig {
        /**
         * 最大缓存条目数
         */
        private long maxSize = 100_000;

        /**
         * 写入后过期时间（分钟）
         */
        private long expireAfterWriteMinutes = 30;

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public long getExpireAfterWriteMinutes() {
            return expireAfterWriteMinutes;
        }

        public void setExpireAfterWriteMinutes(long expireAfterWriteMinutes) {
            this.expireAfterWriteMinutes = expireAfterWriteMinutes;
        }

        public Duration getExpireAfterWrite() {
            return Duration.ofMinutes(expireAfterWriteMinutes);
        }
    }

    /**
     * SplitLocalCache 缓存配置
     */
    public static class SplitLocalCacheConfig {
        /**
         * 最大缓存条目数
         */
        private long maxSize = 50_000;

        /**
         * 写入后过期时间（分钟）
         */
        private long expireAfterWriteMinutes = 60;

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public long getExpireAfterWriteMinutes() {
            return expireAfterWriteMinutes;
        }

        public void setExpireAfterWriteMinutes(long expireAfterWriteMinutes) {
            this.expireAfterWriteMinutes = expireAfterWriteMinutes;
        }

        public Duration getExpireAfterWrite() {
            return Duration.ofMinutes(expireAfterWriteMinutes);
        }
    }

    /**
     * WorkerState 缓存配置
     */
    public static class WorkerStateCacheConfig {
        /**
         * 最大缓存条目数
         */
        private long maxSize = 1_000;

        /**
         * 写入后过期时间（分钟）
         */
        private long expireAfterWriteMinutes = 5;

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public long getExpireAfterWriteMinutes() {
            return expireAfterWriteMinutes;
        }

        public void setExpireAfterWriteMinutes(long expireAfterWriteMinutes) {
            this.expireAfterWriteMinutes = expireAfterWriteMinutes;
        }

        public Duration getExpireAfterWrite() {
            return Duration.ofMinutes(expireAfterWriteMinutes);
        }
    }
}
