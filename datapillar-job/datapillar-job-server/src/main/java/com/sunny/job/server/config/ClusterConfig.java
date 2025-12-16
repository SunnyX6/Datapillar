package com.sunny.job.server.config;

import com.sunny.job.core.id.IdGenerator;
import com.sunny.job.server.broadcast.JobRunBroadcaster;
import com.sunny.job.server.broadcast.WorkflowBroadcaster;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Pekko Cluster 配置（Server 端）
 * <p>
 * Server 角色：
 * - 加入 Worker 集群
 * - 只做 CRDT 广播，不参与调度
 * - 支持多实例部署，保证 HA
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Configuration
public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);

    /**
     * 分布式 ID 生成器
     * <p>
     * 基于 Pekko Cluster 地址生成节点 ID
     */
    @Bean
    @DependsOn("actorSystem")
    public IdGenerator idGenerator(ActorSystem<Void> actorSystem) {
        String address = Cluster.get(actorSystem).selfMember().address().toString();
        IdGenerator generator = IdGenerator.fromAddress(address);
        log.info("初始化 IdGenerator: nodeId={}, address={}", generator.getNodeId(), address);
        return generator;
    }

    /**
     * 工作流广播器
     * <p>
     * 使用 CRDT 广播工作流事件给所有 Worker
     */
    @Bean
    @DependsOn("actorSystem")
    public WorkflowBroadcaster workflowBroadcaster(ActorSystem<Void> actorSystem) {
        log.info("初始化 WorkflowBroadcaster...");
        return new WorkflowBroadcaster(actorSystem);
    }

    /**
     * 任务级广播器
     * <p>
     * 使用 CRDT 广播任务级事件给所有 Worker
     */
    @Bean
    @DependsOn("actorSystem")
    public JobRunBroadcaster jobRunBroadcaster(ActorSystem<Void> actorSystem) {
        log.info("初始化 JobRunBroadcaster...");
        return new JobRunBroadcaster(actorSystem);
    }
}
