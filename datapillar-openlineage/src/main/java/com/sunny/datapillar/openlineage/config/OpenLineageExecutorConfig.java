package com.sunny.datapillar.openlineage.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Actuator configuration. */
@Configuration
public class OpenLineageExecutorConfig {

  @Bean(name = "openLineageExecutor")
  public Executor openLineageExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ol-core-");
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(32);
    executor.setQueueCapacity(2000);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean(name = "openLineageWorkerExecutor")
  public Executor openLineageWorkerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ol-worker-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(1000);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
