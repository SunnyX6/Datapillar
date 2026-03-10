package com.sunny.datapillar.openlineage.config;

import com.sunny.datapillar.openlineage.web.security.keystore.KeyStorageProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** OpenLineage runtime config and properties. */
@Data
@Configuration
@ConfigurationProperties(prefix = "openlineage")
@EnableConfigurationProperties(KeyStorageProperties.class)
public class OpenLineageRuntimeConfig {

  private final Mq mq = new Mq();
  private final Rebuild rebuild = new Rebuild();

  @Data
  public static class Mq {
    private final Topic topic = new Topic();
    private final Group group = new Group();
    private final Retry retry = new Retry();
    private final Bootstrap bootstrap = new Bootstrap();
    private long sendTimeoutMillis = 5000L;
  }

  @Data
  public static class Topic {
    private String events = "dp.openlineage.events";
    private String embedding = "dp.openlineage.embedding";
    private String rebuildCommand = "dp.openlineage.rebuild.command";
    private String eventsDlq = "dp.openlineage.events.dlq";
    private String embeddingDlq = "dp.openlineage.embedding.dlq";
    private String rebuildCommandDlq = "dp.openlineage.rebuild.command.dlq";
  }

  @Data
  public static class Group {
    private String graphConsumer = "ol-graph-consumer";
    private String embeddingConsumer = "ol-embedding-consumer";
    private String rebuildConsumer = "ol-rebuild-consumer";
  }

  @Data
  public static class Retry {
    private int maxAttempts = 5;
    private int firstDelaySeconds = 5;
    private int maxDelaySeconds = 300;
  }

  @Data
  public static class Bootstrap {
    private boolean enabled = true;
    private String clusterName = "DefaultCluster";
    private int readQueueNums = 8;
    private int writeQueueNums = 8;
  }

  @Data
  public static class Rebuild {
    private int lockTimeoutSeconds = 0;
    private int embeddingBatchSize = 200;
  }
}
