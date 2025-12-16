package com.sunny.job.worker.service;

import com.sunny.job.worker.domain.entity.JobComponent;
import com.sunny.job.worker.domain.mapper.JobComponentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 任务组件缓存服务
 * <p>
 * 组件数据相对稳定且数量少，启动时全量加载到内存。
 * 定时刷新以支持运行时新增组件。
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
@Service
public class JobComponentCacheService {

    private static final Logger log = LoggerFactory.getLogger(JobComponentCacheService.class);

    /**
     * 刷新间隔（分钟）
     */
    private static final int REFRESH_INTERVAL_MINUTES = 5;

    private final JobComponentMapper componentMapper;

    /**
     * 组件缓存：componentId → JobComponent
     */
    private final Map<Long, JobComponent> cacheById = new ConcurrentHashMap<>();

    /**
     * 组件缓存：componentCode → JobComponent
     */
    private final Map<String, JobComponent> cacheByCode = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "component-cache-refresh");
        t.setDaemon(true);
        return t;
    });

    public JobComponentCacheService(JobComponentMapper componentMapper) {
        this.componentMapper = componentMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
        scheduler.scheduleAtFixedRate(this::refresh, REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("JobComponentCacheService 初始化完成，刷新间隔={}分钟", REFRESH_INTERVAL_MINUTES);
    }

    /**
     * 刷新组件缓存
     */
    public void refresh() {
        try {
            List<JobComponent> components = componentMapper.selectAllEnabled();
            cacheById.clear();
            cacheByCode.clear();
            for (JobComponent component : components) {
                cacheById.put(component.getId(), component);
                cacheByCode.put(component.getComponentCode(), component);
            }
            log.info("组件缓存刷新完成，组件数量={}", components.size());
        } catch (Exception e) {
            log.error("组件缓存刷新失败", e);
        }
    }

    /**
     * 根据组件ID获取组件编码
     *
     * @param componentId 组件ID（job_info.job_type）
     * @return 组件编码（如 SHELL、PYTHON），不存在返回 null
     */
    public String getComponentCode(Long componentId) {
        if (componentId == null) {
            return null;
        }
        JobComponent component = cacheById.get(componentId);
        return component != null ? component.getComponentCode() : null;
    }

    /**
     * 根据组件ID获取组件
     *
     * @param componentId 组件ID
     * @return 组件，不存在返回 null
     */
    public JobComponent getById(Long componentId) {
        return componentId != null ? cacheById.get(componentId) : null;
    }

    /**
     * 根据组件编码获取组件
     *
     * @param componentCode 组件编码
     * @return 组件，不存在返回 null
     */
    public JobComponent getByCode(String componentCode) {
        return componentCode != null ? cacheByCode.get(componentCode) : null;
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cacheById.size();
    }
}
