package com.sunny.job.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Datapillar Job Worker 启动类
 * <p>
 * Worker 职责：
 * - 运行 Pekko Cluster，承载本地 JobScheduler 和 JobExecutor
 * - 通过 CRDT (Bucket) 实现去中心化任务分片
 * - 直接读写 DB，DB 是唯一状态源
 * - 不依赖 Server，完全去中心化
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.sunny.job.worker", "com.sunny.job.core.handler"})
public class DatapillarJobWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatapillarJobWorkerApplication.class, args);
    }
}
