package com.sunny.job.admin.scheduler.route.strategy;

import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobRegistry;
import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.admin.scheduler.route.ExecutorRouter;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;
import com.sunny.job.core.enums.RegistryConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能负载均衡路由策略（基于YARN DRF算法）
 *
 * 两层负载均衡：
 * 1. 执行器组级别：从所有可用的执行器组中，选择负载最低的执行器组
 * 2. 执行器实例级别：在选中的执行器组内，选择负载最低的执行器实例
 *
 * 负载评分算法 - 主导资源公平性(DRF, Dominant Resource Fairness)：
 *
 * 核心思想：
 * - 不依赖执行器容量上限的先验知识
 * - 基于相对份额而非绝对值进行负载评估
 * - 自动识别执行器的资源瓶颈（CPU/内存/任务数）
 *
 * 算法步骤：
 * 1. 计算每个执行器在各资源维度的份额（相对于集群最大值）
 *    - cpuShare = cpuUsage / maxCpuInCluster
 *    - memoryShare = memoryUsage / maxMemoryInCluster
 *    - taskShare = runningTasks / maxTasksInCluster
 * 2. 找到主导资源（份额最大的资源）
 *    - dominantShare = max(cpuShare, memoryShare, taskShare)
 * 3. 主导份额即为负载评分，越小越好
 *
 * 优点：
 * - 运维人员只需添加新的执行器组，系统自动分配负载到新组
 * - 无需配置执行器容量上限，算法自适应
 * - 自动适应异构环境（不同性能的执行器）
 * - 基于YARN生产验证的成熟算法
 *
 * @author sunny
 * @date 2025-11-10
 */
public class ExecutorRouteLoadBalanced extends ExecutorRouter {


    @Override
    public ReturnT<String> route(TriggerParam triggerParam, List<String> addressList) {
        try {
            // 第一步：获取所有自动注册类型的执行器组（address_type=0）
            List<DatapillarJobGroup> allGroups = DatapillarJobAdminConfig.getAdminConfig()
                    .getDatapillarJobGroupMapper()
                    .findByAddressType(0);

            if (allGroups == null || allGroups.isEmpty()) {
                logger.warn("没有找到可用的执行器组，回退到地址列表模式");
                return fallbackToAddressList(addressList);
            }

            // 第二步：获取所有在线的执行器注册信息
            List<DatapillarJobRegistry> allRegistries = DatapillarJobAdminConfig.getAdminConfig()
                    .getDatapillarJobRegistryMapper()
                    .findAll(RegistryConfig.DEAD_TIMEOUT, new Date());

            if (allRegistries == null || allRegistries.isEmpty()) {
                logger.warn("没有找到在线的执行器，回退到地址列表模式");
                return fallbackToAddressList(addressList);
            }

            // 第三步：计算每个执行器组的平均负载
            Map<String, GroupLoadInfo> groupLoadMap = calculateGroupLoad(allGroups, allRegistries);

            if (groupLoadMap.isEmpty()) {
                logger.warn("无法计算执行器组负载，回退到地址列表模式");
                return fallbackToAddressList(addressList);
            }

            // 第四步：选择负载最低的执行器组
            String selectedGroupAppName = selectLowestLoadGroup(groupLoadMap);

            // 第五步：在选中的执行器组内，选择负载最低的执行器实例
            String selectedAddress = selectLowestLoadExecutor(selectedGroupAppName, allRegistries);

            if (selectedAddress == null) {
                logger.warn("在执行器组[{}]中未找到可用执行器，回退到地址列表模式", selectedGroupAppName);
                return fallbackToAddressList(addressList);
            }

            logger.info("智能负载均衡选择结果 - 执行器组: {}, 执行器地址: {}, 组平均负载: {}",
                    selectedGroupAppName, selectedAddress, groupLoadMap.get(selectedGroupAppName).avgLoadScore);

            return ReturnT.ofSuccess(selectedAddress);

        } catch (Exception e) {
            logger.error("负载均衡路由失败，回退到地址列表模式", e);
            return fallbackToAddressList(addressList);
        }
    }

    /**
     * 回退到传统的地址列表模式（兼容性保障）
     */
    private ReturnT<String> fallbackToAddressList(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            return ReturnT.ofFail("执行器地址列表为空");
        }

        // 如果只有一个执行器，直接返回
        if (addressList.size() == 1) {
            return ReturnT.ofSuccess(addressList.get(0));
        }

        // 收集所有可用的执行器注册信息
        List<DatapillarJobRegistry> availableRegistries = new ArrayList<>();
        for (String address : addressList) {
            try {
                DatapillarJobRegistry registry = DatapillarJobAdminConfig.getAdminConfig()
                        .getDatapillarJobRegistryMapper()
                        .findByRegistryValue(address);
                if (registry != null) {
                    availableRegistries.add(registry);
                }
            } catch (Exception e) {
                logger.error("获取执行器[{}]负载信息失败", address, e);
            }
        }

        // 如果没有可用的注册信息，返回第一个地址
        if (availableRegistries.isEmpty()) {
            return ReturnT.ofSuccess(addressList.get(0));
        }

        // 计算集群资源最大值
        ClusterResourceMax clusterMax = calculateClusterMax(availableRegistries);

        String selectedAddress = null;
        double minLoadScore = Double.MAX_VALUE;

        // 遍历所有执行器地址，选择负载最低的
        for (DatapillarJobRegistry registry : availableRegistries) {
            try {
                double loadScore = calculateLoadScore(
                        registry.getCpuUsage(),
                        registry.getMemoryUsage(),
                        registry.getRunningTasks(),
                        clusterMax
                );

                if (loadScore < minLoadScore) {
                    minLoadScore = loadScore;
                    selectedAddress = registry.getRegistryValue();
                }

            } catch (Exception e) {
                logger.error("计算执行器[{}]负载评分失败", registry.getRegistryValue(), e);
            }
        }

        if (selectedAddress == null) {
            selectedAddress = addressList.get(0);
        }

        return ReturnT.ofSuccess(selectedAddress);
    }

    /**
     * 计算每个执行器组的平均负载
     */
    private Map<String, GroupLoadInfo> calculateGroupLoad(List<DatapillarJobGroup> allGroups, List<DatapillarJobRegistry> allRegistries) {
        // 第一步：计算集群级别的资源最大值（用于DRF算法）
        ClusterResourceMax clusterMax = calculateClusterMax(allRegistries);

        Map<String, GroupLoadInfo> groupLoadMap = new HashMap<>();

        for (DatapillarJobGroup group : allGroups) {
            GroupLoadInfo groupInfo = new GroupLoadInfo();
            groupInfo.appName = group.getAppname();
            groupInfo.groupId = group.getId();

            double totalLoad = 0;
            int executorCount = 0;

            // 统计该组下所有执行器的负载
            for (DatapillarJobRegistry registry : allRegistries) {
                if (RegistryConfig.RegistType.EXECUTOR.name().equals(registry.getRegistryGroup())
                        && group.getAppname().equals(registry.getRegistryKey())) {

                    double loadScore = calculateLoadScore(
                            registry.getCpuUsage(),
                            registry.getMemoryUsage(),
                            registry.getRunningTasks(),
                            clusterMax
                    );

                    totalLoad += loadScore;
                    executorCount++;
                }
            }

            if (executorCount > 0) {
                groupInfo.avgLoadScore = totalLoad / executorCount;
                groupInfo.executorCount = executorCount;
                groupLoadMap.put(group.getAppname(), groupInfo);

                logger.debug("执行器组[{}] - 执行器数量: {}, 平均负载: {}",
                        group.getAppname(), executorCount, groupInfo.avgLoadScore);
            }
        }

        return groupLoadMap;
    }

    /**
     * 选择负载最低的执行器组
     */
    private String selectLowestLoadGroup(Map<String, GroupLoadInfo> groupLoadMap) {
        String selectedGroup = null;
        double minAvgLoad = Double.MAX_VALUE;

        for (Map.Entry<String, GroupLoadInfo> entry : groupLoadMap.entrySet()) {
            if (entry.getValue().avgLoadScore < minAvgLoad) {
                minAvgLoad = entry.getValue().avgLoadScore;
                selectedGroup = entry.getKey();
            }
        }

        return selectedGroup;
    }

    /**
     * 在指定执行器组内选择负载最低的执行器实例
     */
    private String selectLowestLoadExecutor(String groupAppName, List<DatapillarJobRegistry> allRegistries) {
        // 第一步：计算集群级别的资源最大值
        ClusterResourceMax clusterMax = calculateClusterMax(allRegistries);

        String selectedAddress = null;
        double minLoadScore = Double.MAX_VALUE;

        for (DatapillarJobRegistry registry : allRegistries) {
            if (RegistryConfig.RegistType.EXECUTOR.name().equals(registry.getRegistryGroup())
                    && groupAppName.equals(registry.getRegistryKey())) {

                double loadScore = calculateLoadScore(
                        registry.getCpuUsage(),
                        registry.getMemoryUsage(),
                        registry.getRunningTasks(),
                        clusterMax
                );

                logger.debug("执行器组[{}]中的执行器[{}] - 负载评分: {}, CPU: {}%, 内存: {}%, 任务数: {}",
                        groupAppName, registry.getRegistryValue(), loadScore,
                        registry.getCpuUsage(), registry.getMemoryUsage(), registry.getRunningTasks());

                if (loadScore < minLoadScore) {
                    minLoadScore = loadScore;
                    selectedAddress = registry.getRegistryValue();
                }
            }
        }

        return selectedAddress;
    }

    /**
     * 执行器组负载信息
     */
    private static class GroupLoadInfo {
        String appName;          // 执行器组应用名
        int groupId;             // 执行器组ID
        double avgLoadScore;     // 平均负载评分
        int executorCount;       // 执行器数量
    }

    /**
     * 集群资源最大值（用于DRF算法）
     */
    private static class ClusterResourceMax {
        double maxCpu;           // 集群中CPU使用率的最大值
        double maxMemory;        // 集群中内存使用率的最大值
        int maxTasks;            // 集群中运行任务数的最大值
    }

    /**
     * 计算集群级别的资源最大值
     *
     * @param registries 所有执行器注册信息
     * @return 集群资源最大值
     */
    private ClusterResourceMax calculateClusterMax(List<DatapillarJobRegistry> registries) {
        ClusterResourceMax clusterMax = new ClusterResourceMax();
        clusterMax.maxCpu = 0.0;
        clusterMax.maxMemory = 0.0;
        clusterMax.maxTasks = 0;

        for (DatapillarJobRegistry registry : registries) {
            if (RegistryConfig.RegistType.EXECUTOR.name().equals(registry.getRegistryGroup())) {
                double cpu = (registry.getCpuUsage() != null) ? registry.getCpuUsage() : 0.0;
                double memory = (registry.getMemoryUsage() != null) ? registry.getMemoryUsage() : 0.0;
                int tasks = (registry.getRunningTasks() != null) ? registry.getRunningTasks() : 0;

                if (cpu > clusterMax.maxCpu) {
                    clusterMax.maxCpu = cpu;
                }
                if (memory > clusterMax.maxMemory) {
                    clusterMax.maxMemory = memory;
                }
                if (tasks > clusterMax.maxTasks) {
                    clusterMax.maxTasks = tasks;
                }
            }
        }

        // 避免除零错误，设置最小值为1.0
        if (clusterMax.maxCpu < 1.0) {
            clusterMax.maxCpu = 1.0;
        }
        if (clusterMax.maxMemory < 1.0) {
            clusterMax.maxMemory = 1.0;
        }
        if (clusterMax.maxTasks < 1) {
            clusterMax.maxTasks = 1;
        }

        return clusterMax;
    }

    /**
     * 计算负载评分（基于YARN DRF算法）
     *
     * 核心思想：
     * 1. 计算每个资源维度的份额（相对于集群最大值）
     * 2. 找到主导资源（份额最大的资源）
     * 3. 主导份额即为负载评分
     *
     * @param cpuUsage      CPU使用率(0-100)
     * @param memoryUsage   内存使用率(0-100)
     * @param runningTasks  运行中的任务数
     * @param clusterMax    集群资源最大值
     * @return 负载评分(0-100)，越低越好
     */
    private double calculateLoadScore(Double cpuUsage, Double memoryUsage, Integer runningTasks, ClusterResourceMax clusterMax) {
        // 处理null值，默认为0
        double cpu = (cpuUsage != null) ? cpuUsage : 0.0;
        double memory = (memoryUsage != null) ? memoryUsage : 0.0;
        int tasks = (runningTasks != null) ? runningTasks : 0;

        // 计算每种资源的份额（相对于集群最大值）
        double cpuShare = cpu / clusterMax.maxCpu;
        double memoryShare = memory / clusterMax.maxMemory;
        double taskShare = (double) tasks / clusterMax.maxTasks;

        // 找到主导资源（份额最大的资源）
        double dominantShare = Math.max(Math.max(cpuShare, memoryShare), taskShare);

        // 返回主导份额作为负载评分（转换为0-100的百分比）
        return dominantShare * 100.0;
    }
}
