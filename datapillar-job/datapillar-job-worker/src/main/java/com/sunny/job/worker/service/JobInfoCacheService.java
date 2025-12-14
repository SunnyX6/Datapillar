package com.sunny.job.worker.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.worker.domain.entity.JobInfo;
import com.sunny.job.worker.domain.mapper.JobInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 任务定义缓存服务
 * <p>
 * job_info 是任务定义表，数据相对稳定，不需要每次查询都 JOIN。
 * 本服务将 job_info 数据按需缓存到内存，消除冗余 JOIN，提升查询性能。
 * <p>
 * 缓存策略：
 * - 使用 Caffeine 高性能缓存（无锁设计，线程安全）
 * - LRU 淘汰：超过最大容量时淘汰最久未访问的记录
 * - 写入后 30 分钟过期：保证数据最终一致性
 * - 按需加载：首次访问时从 DB 加载
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Service
public class JobInfoCacheService {

    private static final Logger log = LoggerFactory.getLogger(JobInfoCacheService.class);

    /**
     * 缓存最大容量
     */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 缓存过期时间（分钟）
     */
    private static final int EXPIRE_MINUTES = 30;

    private final JobInfoMapper jobInfoMapper;

    /**
     * Caffeine 缓存：jobId → JobInfo
     * <p>
     * 高性能无锁设计，替代 synchronized + LinkedHashMap
     */
    private final Cache<Long, JobInfo> cache;

    public JobInfoCacheService(JobInfoMapper jobInfoMapper) {
        this.jobInfoMapper = jobInfoMapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(Duration.ofMinutes(EXPIRE_MINUTES))
                .recordStats()
                .build();
        log.info("JobInfoCacheService 初始化完成，最大缓存容量={}，过期时间={}分钟", MAX_CACHE_SIZE, EXPIRE_MINUTES);
    }

    /**
     * 获取任务定义（按需加载，线程安全）
     * <p>
     * 使用 Caffeine 的 get() 方法，保证同一个 key 只会加载一次
     *
     * @param jobId 任务 ID
     * @return 任务定义，不存在返回 null
     */
    public JobInfo get(long jobId) {
        return cache.get(jobId, this::loadFromDb);
    }

    /**
     * 从数据库加载任务定义
     */
    private JobInfo loadFromDb(Long jobId) {
        JobInfo fromDb = jobInfoMapper.selectById(jobId);
        if (fromDb != null) {
            log.debug("缓存加载: jobId={}, jobName={}", jobId, fromDb.getJobName());
        }
        return fromDb;
    }

    /**
     * 使单个缓存失效（任务定义更新时调用）
     *
     * @param jobId 任务 ID
     */
    public void invalidate(long jobId) {
        cache.invalidate(jobId);
        log.debug("缓存失效: jobId={}", jobId);
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计字符串（命中率、加载次数等）
     */
    public String getCacheStats() {
        return cache.stats().toString();
    }
}
