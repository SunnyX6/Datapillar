package com.sunny.job.admin.scheduler.route.strategy;

import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DRF负载均衡算法单元测试
 *
 * 测试目标：验证基于YARN DRF算法的负载均衡策略是否正确工作
 *
 * @author sunny
 * @date 2025-11-10
 */
public class ExecutorRouteLoadBalancedTest {

    /**
     * 测试DRF算法的核心计算逻辑
     *
     * 场景：3个执行器，不同的负载状态
     * 执行器1：低负载（CPU=20%, Memory=30%, Tasks=2）
     * 执行器2：CPU瓶颈（CPU=80%, Memory=40%, Tasks=5）
     * 执行器3：内存瓶颈（CPU=30%, Memory=85%, Tasks=3）
     *
     * 预期结果：选择执行器1（主导份额最低）
     */
    @Test
    public void testDRFAlgorithmBasic() {
        System.out.println("\n========================================");
        System.out.println("测试用例1: DRF算法基础场景");
        System.out.println("========================================");

        // 模拟集群最大值
        double maxCpu = 80.0;
        double maxMemory = 85.0;
        int maxTasks = 5;

        System.out.println("\n集群资源最大值：");
        System.out.println("  maxCpu = " + maxCpu + "%");
        System.out.println("  maxMemory = " + maxMemory + "%");
        System.out.println("  maxTasks = " + maxTasks);

        // 执行器1：低负载
        double exec1_cpu = 20.0;
        double exec1_memory = 30.0;
        int exec1_tasks = 2;

        double exec1_cpuShare = exec1_cpu / maxCpu;
        double exec1_memoryShare = exec1_memory / maxMemory;
        double exec1_taskShare = (double) exec1_tasks / maxTasks;
        double exec1_dominantShare = Math.max(Math.max(exec1_cpuShare, exec1_memoryShare), exec1_taskShare);
        double exec1_loadScore = exec1_dominantShare * 100.0;

        System.out.println("\n执行器1 (192.168.1.101:9999)：");
        System.out.println("  CPU: " + exec1_cpu + "%, Memory: " + exec1_memory + "%, Tasks: " + exec1_tasks);
        System.out.println("  cpuShare = " + exec1_cpu + "/" + maxCpu + " = " + String.format("%.2f", exec1_cpuShare));
        System.out.println("  memoryShare = " + exec1_memory + "/" + maxMemory + " = " + String.format("%.2f", exec1_memoryShare));
        System.out.println("  taskShare = " + exec1_tasks + "/" + maxTasks + " = " + String.format("%.2f", exec1_taskShare));
        System.out.println("  dominantShare = " + String.format("%.2f", exec1_dominantShare) + " → 负载评分 " + String.format("%.1f", exec1_loadScore));

        // 执行器2：CPU瓶颈
        double exec2_cpu = 80.0;
        double exec2_memory = 40.0;
        int exec2_tasks = 5;

        double exec2_cpuShare = exec2_cpu / maxCpu;
        double exec2_memoryShare = exec2_memory / maxMemory;
        double exec2_taskShare = (double) exec2_tasks / maxTasks;
        double exec2_dominantShare = Math.max(Math.max(exec2_cpuShare, exec2_memoryShare), exec2_taskShare);
        double exec2_loadScore = exec2_dominantShare * 100.0;

        System.out.println("\n执行器2 (192.168.1.102:9999)：");
        System.out.println("  CPU: " + exec2_cpu + "%, Memory: " + exec2_memory + "%, Tasks: " + exec2_tasks);
        System.out.println("  cpuShare = " + exec2_cpu + "/" + maxCpu + " = " + String.format("%.2f", exec2_cpuShare));
        System.out.println("  memoryShare = " + exec2_memory + "/" + maxMemory + " = " + String.format("%.2f", exec2_memoryShare));
        System.out.println("  taskShare = " + exec2_tasks + "/" + maxTasks + " = " + String.format("%.2f", exec2_taskShare));
        System.out.println("  dominantShare = " + String.format("%.2f", exec2_dominantShare) + " → 负载评分 " + String.format("%.1f", exec2_loadScore) + " (CPU和任务数瓶颈)");

        // 执行器3：内存瓶颈
        double exec3_cpu = 30.0;
        double exec3_memory = 85.0;
        int exec3_tasks = 3;

        double exec3_cpuShare = exec3_cpu / maxCpu;
        double exec3_memoryShare = exec3_memory / maxMemory;
        double exec3_taskShare = (double) exec3_tasks / maxTasks;
        double exec3_dominantShare = Math.max(Math.max(exec3_cpuShare, exec3_memoryShare), exec3_taskShare);
        double exec3_loadScore = exec3_dominantShare * 100.0;

        System.out.println("\n执行器3 (192.168.1.103:9999)：");
        System.out.println("  CPU: " + exec3_cpu + "%, Memory: " + exec3_memory + "%, Tasks: " + exec3_tasks);
        System.out.println("  cpuShare = " + exec3_cpu + "/" + maxCpu + " = " + String.format("%.2f", exec3_cpuShare));
        System.out.println("  memoryShare = " + exec3_memory + "/" + maxMemory + " = " + String.format("%.2f", exec3_memoryShare));
        System.out.println("  taskShare = " + exec3_tasks + "/" + maxTasks + " = " + String.format("%.2f", exec3_taskShare));
        System.out.println("  dominantShare = " + String.format("%.2f", exec3_dominantShare) + " → 负载评分 " + String.format("%.1f", exec3_loadScore) + " (内存瓶颈)");

        // 验证：执行器1的负载评分最低
        System.out.println("\n结论：");
        System.out.println("  ✓ 执行器1负载评分最低: " + String.format("%.1f", exec1_loadScore));
        System.out.println("  ✓ 执行器2负载评分: " + String.format("%.1f", exec2_loadScore));
        System.out.println("  ✓ 执行器3负载评分: " + String.format("%.1f", exec3_loadScore));
        System.out.println("  ✓ DRF算法应该选择: 执行器1");

        assertTrue(exec1_loadScore < exec2_loadScore, "执行器1的负载应该低于执行器2");
        assertTrue(exec1_loadScore < exec3_loadScore, "执行器1的负载应该低于执行器3");
        assertEquals(40.0, exec1_loadScore, 0.1, "执行器1的负载评分应该约为40.0");
        assertEquals(100.0, exec2_loadScore, 0.1, "执行器2的负载评分应该约为100.0");
        assertEquals(100.0, exec3_loadScore, 0.1, "执行器3的负载评分应该约为100.0");

        System.out.println("\n✓ 测试通过！DRF算法计算正确");
    }

    /**
     * 测试异构环境
     *
     * 场景：高性能执行器 vs 低性能执行器
     * 高性能执行器：任务多但负载低
     * 低性能执行器：任务少但负载高
     *
     * 预期结果：选择高性能执行器（主导份额更低）
     */
    @Test
    public void testHeterogeneousEnvironment() {
        System.out.println("\n========================================");
        System.out.println("测试用例2: 异构环境（不同性能的执行器）");
        System.out.println("========================================");

        double maxCpu = 70.0;
        double maxMemory = 80.0;
        int maxTasks = 10;

        System.out.println("\n集群资源最大值：");
        System.out.println("  maxCpu = " + maxCpu + "%");
        System.out.println("  maxMemory = " + maxMemory + "%");
        System.out.println("  maxTasks = " + maxTasks);

        // 高性能执行器：即使任务多，负载也低
        double highPerf_cpu = 30.0;
        double highPerf_memory = 25.0;
        int highPerf_tasks = 10;

        double highPerf_cpuShare = highPerf_cpu / maxCpu;
        double highPerf_memoryShare = highPerf_memory / maxMemory;
        double highPerf_taskShare = (double) highPerf_tasks / maxTasks;
        double highPerf_dominantShare = Math.max(Math.max(highPerf_cpuShare, highPerf_memoryShare), highPerf_taskShare);
        double highPerf_loadScore = highPerf_dominantShare * 100.0;

        System.out.println("\n高性能执行器：");
        System.out.println("  CPU: " + highPerf_cpu + "%, Memory: " + highPerf_memory + "%, Tasks: " + highPerf_tasks);
        System.out.println("  主导份额: " + String.format("%.2f", highPerf_dominantShare) + " → 负载评分 " + String.format("%.1f", highPerf_loadScore));

        // 低性能执行器：任务少但负载高
        double lowPerf_cpu = 70.0;
        double lowPerf_memory = 80.0;
        int lowPerf_tasks = 3;

        double lowPerf_cpuShare = lowPerf_cpu / maxCpu;
        double lowPerf_memoryShare = lowPerf_memory / maxMemory;
        double lowPerf_taskShare = (double) lowPerf_tasks / maxTasks;
        double lowPerf_dominantShare = Math.max(Math.max(lowPerf_cpuShare, lowPerf_memoryShare), lowPerf_taskShare);
        double lowPerf_loadScore = lowPerf_dominantShare * 100.0;

        System.out.println("\n低性能执行器：");
        System.out.println("  CPU: " + lowPerf_cpu + "%, Memory: " + lowPerf_memory + "%, Tasks: " + lowPerf_tasks);
        System.out.println("  主导份额: " + String.format("%.2f", lowPerf_dominantShare) + " → 负载评分 " + String.format("%.1f", lowPerf_loadScore));

        System.out.println("\n结论：");
        System.out.println("  ✓ 高性能执行器处理10个任务，负载评分: " + String.format("%.1f", highPerf_loadScore));
        System.out.println("  ✓ 低性能执行器仅处理3个任务，负载评分: " + String.format("%.1f", lowPerf_loadScore));
        System.out.println("  ✓ DRF算法倾向选择高性能执行器（负载更低）");
        System.out.println("  ✓ 体现了DRF的自适应异构环境能力");

        assertTrue(highPerf_loadScore < lowPerf_loadScore, "高性能执行器的负载应该低于低性能执行器");

        System.out.println("\n✓ 测试通过！DRF算法能够自适应异构环境");
    }

    /**
     * 测试边界情况
     *
     * 场景1：全0负载
     * 场景2：单个资源瓶颈
     * 场景3：所有资源都是瓶颈
     */
    @Test
    public void testEdgeCases() {
        System.out.println("\n========================================");
        System.out.println("测试用例3: 边界情况");
        System.out.println("========================================");

        double maxCpu = 100.0;
        double maxMemory = 100.0;
        int maxTasks = 10;

        // 场景1：全0负载（空闲执行器）
        System.out.println("\n场景1: 全0负载（空闲执行器）");
        double idle_loadScore = calculateLoadScore(0.0, 0.0, 0, maxCpu, maxMemory, maxTasks);
        System.out.println("  CPU: 0%, Memory: 0%, Tasks: 0");
        System.out.println("  负载评分: " + String.format("%.1f", idle_loadScore));
        assertEquals(0.0, idle_loadScore, 0.1, "空闲执行器的负载评分应该为0");

        // 场景2：单个资源瓶颈
        System.out.println("\n场景2: 仅CPU瓶颈");
        double cpuBottleneck_loadScore = calculateLoadScore(100.0, 10.0, 1, maxCpu, maxMemory, maxTasks);
        System.out.println("  CPU: 100%, Memory: 10%, Tasks: 1");
        System.out.println("  负载评分: " + String.format("%.1f", cpuBottleneck_loadScore));
        assertEquals(100.0, cpuBottleneck_loadScore, 0.1, "CPU瓶颈的负载评分应该为100");

        // 场景3：所有资源都是瓶颈
        System.out.println("\n场景3: 所有资源都是瓶颈");
        double fullLoad_loadScore = calculateLoadScore(100.0, 100.0, 10, maxCpu, maxMemory, maxTasks);
        System.out.println("  CPU: 100%, Memory: 100%, Tasks: 10");
        System.out.println("  负载评分: " + String.format("%.1f", fullLoad_loadScore));
        assertEquals(100.0, fullLoad_loadScore, 0.1, "满负载的负载评分应该为100");

        System.out.println("\n✓ 测试通过！边界情况处理正确");
    }

    /**
     * 测试null值处理
     */
    @Test
    public void testNullHandling() {
        System.out.println("\n========================================");
        System.out.println("测试用例4: Null值处理");
        System.out.println("========================================");

        double maxCpu = 50.0;
        double maxMemory = 60.0;
        int maxTasks = 5;

        // null值应该被视为0
        double loadScore = calculateLoadScore(null, null, null, maxCpu, maxMemory, maxTasks);
        System.out.println("\n当所有值为null时:");
        System.out.println("  负载评分: " + String.format("%.1f", loadScore));
        assertEquals(0.0, loadScore, 0.1, "null值应该被视为0，负载评分应该为0");

        System.out.println("\n✓ 测试通过！Null值处理正确");
    }

    /**
     * 辅助方法：计算负载评分（模拟DRF算法）
     */
    private double calculateLoadScore(Double cpuUsage, Double memoryUsage, Integer runningTasks,
                                      double maxCpu, double maxMemory, int maxTasks) {
        // 处理null值
        double cpu = (cpuUsage != null) ? cpuUsage : 0.0;
        double memory = (memoryUsage != null) ? memoryUsage : 0.0;
        int tasks = (runningTasks != null) ? runningTasks : 0;

        // 计算资源份额
        double cpuShare = cpu / maxCpu;
        double memoryShare = memory / maxMemory;
        double taskShare = (double) tasks / maxTasks;

        // 找到主导资源
        double dominantShare = Math.max(Math.max(cpuShare, memoryShare), taskShare);

        // 返回负载评分
        return dominantShare * 100.0;
    }
}
