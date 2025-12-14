package com.sunny.job.worker.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.PreDestroy;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Pekko ActorSystem 配置
 * <p>
 * 创建并管理 Pekko Cluster ActorSystem 生命周期
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Configuration
public class PekkoConfig {

    private static final Logger log = LoggerFactory.getLogger(PekkoConfig.class);

    private static final String ACTOR_SYSTEM_NAME = "datapillar-job";

    @Value("${datapillar.job.worker.pekko.host:127.0.0.1}")
    private String host;

    @Value("${datapillar.job.worker.pekko.port:2551}")
    private int port;

    @Value("${datapillar.job.worker.pekko.seed-nodes:pekko://datapillar-job@127.0.0.1:2551}")
    private String seedNodes;

    private ActorSystem<Void> actorSystem;

    @Bean
    public ActorSystem<Void> actorSystem() {
        log.info("初始化 Pekko ActorSystem: host={}, port={}", host, port);

        Config config = createConfig();
        actorSystem = ActorSystem.create(Behaviors.empty(), ACTOR_SYSTEM_NAME, config);

        log.info("Pekko ActorSystem 启动成功: {}", actorSystem.name());
        return actorSystem;
    }

    private Config createConfig() {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("pekko.remote.artery.canonical.hostname", host);
        overrides.put("pekko.remote.artery.canonical.port", port);

        String[] seeds = seedNodes.split(",");
        StringBuilder seedNodesList = new StringBuilder("[");
        for (int i = 0; i < seeds.length; i++) {
            if (i > 0) {
                seedNodesList.append(",");
            }
            seedNodesList.append("\"").append(seeds[i].trim()).append("\"");
        }
        seedNodesList.append("]");
        overrides.put("pekko.cluster.seed-nodes", ConfigFactory.parseString(
                "seed-nodes = " + seedNodesList).getList("seed-nodes"));

        Config overrideConfig = ConfigFactory.parseMap(overrides);
        Config workerConfig = ConfigFactory.parseResources("worker.conf");
        return overrideConfig.withFallback(workerConfig).withFallback(ConfigFactory.load());
    }

    @PreDestroy
    public void shutdown() {
        if (actorSystem != null) {
            log.info("关闭 Pekko ActorSystem...");
            actorSystem.terminate();
            try {
                actorSystem.getWhenTerminated().toCompletableFuture().get();
                log.info("Pekko ActorSystem 已关闭");
            } catch (Exception e) {
                log.error("等待 ActorSystem 关闭时出错", e);
            }
        }
    }
}
