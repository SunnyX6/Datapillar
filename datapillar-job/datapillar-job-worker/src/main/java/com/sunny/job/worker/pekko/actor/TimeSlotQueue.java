package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.message.JobRunInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 时间槽队列（优化 PriorityQueue）
 * <p>
 * 核心优化：
 * - 按秒分桶，使用 TreeMap 存储时间槽
 * - 使用 HashMap 做快速索引
 * - remove 操作从 O(n) 优化为 O(1)
 * - pollExpired 操作从 O(n) 优化为 O(k)，k = 到期任务数
 * <p>
 * 数据结构：
 * - timeSlots: TreeMap<秒级时间戳, Set<jobRunId>>，按时间排序
 * - jobIndex: HashMap<jobRunId, JobRunInfo>，快速查找
 * <p>
 * 线程安全：
 * - 本类非线程安全，只应在 Actor 消息处理线程中使用
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class TimeSlotQueue {

    /**
     * 时间槽：秒级时间戳 → Set<jobRunId>
     */
    private final TreeMap<Long, Set<Long>> timeSlots = new TreeMap<>();

    /**
     * 任务索引：jobRunId → JobRunInfo
     */
    private final Map<Long, JobRunInfo> jobIndex = new HashMap<>();

    /**
     * 任务时间索引：jobRunId → 秒级时间戳（用于快速定位 remove）
     */
    private final Map<Long, Long> jobTimeIndex = new HashMap<>();

    /**
     * 添加任务到队列
     *
     * @param job 任务信息
     */
    public void add(JobRunInfo job) {
        long jobRunId = job.getJobRunId();

        // 已存在则跳过
        if (jobIndex.containsKey(jobRunId)) {
            return;
        }

        // 计算秒级时间槽
        long slot = job.getTriggerTime() / 1000;

        // 添加到时间槽
        timeSlots.computeIfAbsent(slot, k -> new HashSet<>()).add(jobRunId);

        // 添加到索引
        jobIndex.put(jobRunId, job);
        jobTimeIndex.put(jobRunId, slot);
    }

    /**
     * 从队列移除任务（O(1) 操作）
     *
     * @param job 任务信息
     * @return true 如果成功移除
     */
    public boolean remove(JobRunInfo job) {
        return removeById(job.getJobRunId());
    }

    /**
     * 从队列移除任务（O(1) 操作）
     *
     * @param jobRunId 任务 ID
     * @return true 如果成功移除
     */
    public boolean removeById(long jobRunId) {
        Long slot = jobTimeIndex.remove(jobRunId);
        if (slot == null) {
            return false;
        }

        JobRunInfo removed = jobIndex.remove(jobRunId);
        if (removed == null) {
            return false;
        }

        Set<Long> jobs = timeSlots.get(slot);
        if (jobs != null) {
            jobs.remove(jobRunId);
            // 如果时间槽为空，移除该槽
            if (jobs.isEmpty()) {
                timeSlots.remove(slot);
            }
        }

        return true;
    }

    /**
     * 查看队首任务（不移除）
     *
     * @return 最早触发的任务，队列为空返回 null
     */
    public JobRunInfo peek() {
        if (timeSlots.isEmpty()) {
            return null;
        }

        Map.Entry<Long, Set<Long>> first = timeSlots.firstEntry();
        if (first == null || first.getValue().isEmpty()) {
            return null;
        }

        // 获取该时间槽中优先级最高的任务
        JobRunInfo best = null;
        for (Long jobRunId : first.getValue()) {
            JobRunInfo job = jobIndex.get(jobRunId);
            if (job != null) {
                if (best == null || comparePriority(job, best) < 0) {
                    best = job;
                }
            }
        }
        return best;
    }

    /**
     * 弹出队首任务
     *
     * @return 最早触发的任务，队列为空返回 null
     */
    public JobRunInfo poll() {
        JobRunInfo job = peek();
        if (job != null) {
            removeById(job.getJobRunId());
        }
        return job;
    }

    /**
     * 弹出所有已到期的任务
     *
     * @param now 当前时间（毫秒）
     * @return 到期的任务列表（按触发时间和优先级排序）
     */
    public List<JobRunInfo> pollExpired(long now) {
        long currentSlot = now / 1000;
        List<JobRunInfo> expired = new ArrayList<>();

        // 遍历所有已过期的时间槽
        while (!timeSlots.isEmpty()) {
            Map.Entry<Long, Set<Long>> first = timeSlots.firstEntry();
            if (first.getKey() > currentSlot) {
                break;
            }

            Set<Long> jobRunIds = timeSlots.pollFirstEntry().getValue();
            for (Long jobRunId : jobRunIds) {
                JobRunInfo job = jobIndex.remove(jobRunId);
                if (job != null) {
                    jobTimeIndex.remove(jobRunId);
                    expired.add(job);
                }
            }
        }

        // 按触发时间和优先级排序
        expired.sort(this::comparePriority);

        return expired;
    }

    /**
     * 队列是否为空
     */
    public boolean isEmpty() {
        return jobIndex.isEmpty();
    }

    /**
     * 队列大小
     */
    public int size() {
        return jobIndex.size();
    }

    /**
     * 检查任务是否存在
     */
    public boolean contains(long jobRunId) {
        return jobIndex.containsKey(jobRunId);
    }

    /**
     * 获取任务（不移除）
     */
    public JobRunInfo get(long jobRunId) {
        return jobIndex.get(jobRunId);
    }

    /**
     * 比较优先级
     * <p>
     * 排序规则：
     * 1. 触发时间早的优先
     * 2. 同时间时，优先级高的优先（priority 值大的优先）
     */
    private int comparePriority(JobRunInfo a, JobRunInfo b) {
        int cmp = Long.compare(a.getTriggerTime(), b.getTriggerTime());
        return cmp != 0 ? cmp : Integer.compare(b.getPriority(), a.getPriority());
    }
}
