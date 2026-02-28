/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.listener.openlineage;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineageClient;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.apache.gravitino.listener.api.EventListenerPlugin;
import org.apache.gravitino.listener.api.event.Event;

/**
 * OpenLineage Listener for Gravitino.
 *
 * <p>Captures Gravitino metadata events (Table/Schema/Catalog CRUD) and emits them as standard
 * OpenLineage RunEvents through the OpenLineage Java Client.
 *
 * <p>Configuration example (gravitino.conf):
 *
 * <pre>
 * gravitino.eventListener.names = openlineage
 * gravitino.eventListener.openlineage.class = org.apache.gravitino.listener.openlineage.OpenLineageGravitinoListener
 *
 * # OpenLineage HTTP transport configuration
 * gravitino.openlineage.transport.type = http
 * gravitino.openlineage.transport.url = http://127.0.0.1:7000
 * gravitino.openlineage.transport.endpoint = /api/openlineage
 * gravitino.openlineage.transport.timeoutInMillis = 5000
 * gravitino.openlineage.transport.compression = gzip
 *
 * # Authentication
 * gravitino.openlineage.transport.auth.type = api_key
 * gravitino.openlineage.transport.auth.apiKey = your-api-key
 *
 * # Rate Limiting (Async Queue)
 * gravitino.openlineage.transport.maxQueueSize = 10000
 * gravitino.openlineage.transport.maxConcurrentRequests = 100
 *
 * # Retry Configuration
 * gravitino.openlineage.transport.retry.total = 5
 * gravitino.openlineage.transport.retry.backoffFactor = 0.3
 * </pre>
 */
@Slf4j
public class OpenLineageGravitinoListener implements EventListenerPlugin {

  private OpenLineageClient openLineageClient;
  private OpenLineage openLineage;
  private GravitinoEventConverter converter;
  private OpenLineageClientBuilder clientBuilder;
  private String namespace;

  // 异步执行和限流
  private ExecutorService executorService;
  private Semaphore concurrencySemaphore;
  private int retryTotal;
  private double retryBackoffFactor;

  @Override
  public void init(Map<String, String> properties) throws RuntimeException {
    this.namespace =
        properties.getOrDefault(
            OpenLineageListenerConfig.NAMESPACE, OpenLineageListenerConfig.DEFAULT_NAMESPACE);

    // Build OpenLineage client with transport from configuration
    this.clientBuilder = new OpenLineageClientBuilder(properties);
    this.openLineageClient = clientBuilder.build();
    this.openLineage = new OpenLineage(OpenLineageListenerConfig.PRODUCER_URI);
    this.converter = new GravitinoEventConverter(openLineage, namespace);

    // 获取异步执行器和限流配置
    this.executorService = clientBuilder.getExecutorService();
    this.concurrencySemaphore = clientBuilder.getConcurrencySemaphore();
    this.retryTotal = clientBuilder.getRetryTotal();
    this.retryBackoffFactor = clientBuilder.getRetryBackoffFactor();

    log.info(
        "OpenLineageGravitinoListener initialized: namespace={}, transport={}, "
            + "maxQueueSize={}, maxConcurrentRequests={}, retryTotal={}",
        namespace,
        properties.getOrDefault("transport.type", "console"),
        clientBuilder.getMaxQueueSize(),
        clientBuilder.getMaxConcurrentRequests(),
        retryTotal);
  }

  @Override
  public void start() throws RuntimeException {
    log.info("OpenLineageGravitinoListener started");
  }

  @Override
  public void stop() throws RuntimeException {
    if (clientBuilder != null) {
      clientBuilder.shutdown();
    }
    if (openLineageClient != null) {
      try {
        openLineageClient.close();
      } catch (Exception e) {
        log.warn("Failed to close OpenLineage client", e);
      }
    }
    log.info("OpenLineageGravitinoListener stopped");
  }

  @Override
  public Mode mode() {
    return Mode.ASYNC_ISOLATED;
  }

  @Override
  public void onPostEvent(Event postEvent) throws RuntimeException {
    try {
      OpenLineage.RunEvent runEvent = converter.convert(postEvent);
      if (runEvent != null) {
        // 异步发送事件（带限流和重试）
        emitEventAsync(runEvent);
      }
    } catch (IllegalArgumentException e) {
      if (e.getMessage() != null && e.getMessage().contains("missing tenant_id")) {
        log.warn(
            "Reject OpenLineage event without tenant_id snapshot: {}",
            postEvent.getClass().getSimpleName());
        return;
      }
      log.error(
          "Failed to process OpenLineage event for: {}", postEvent.getClass().getSimpleName(), e);
    } catch (Exception e) {
      log.error(
          "Failed to process OpenLineage event for: {}", postEvent.getClass().getSimpleName(), e);
    }
  }

  /** 异步发送事件（带限流和重试） */
  private void emitEventAsync(OpenLineage.RunEvent runEvent) {
    executorService.submit(
        () -> {
          try {
            // 获取并发许可（限流）
            concurrencySemaphore.acquire();
            try {
              emitWithRetry(runEvent);
            } finally {
              concurrencySemaphore.release();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Event emission interrupted: {}", runEvent.getJob().getName());
          }
        });
  }

  /** 带重试的事件发送 */
  private void emitWithRetry(OpenLineage.RunEvent runEvent) {
    int attempt = 0;
    Exception lastException = null;

    while (attempt < retryTotal) {
      try {
        openLineageClient.emit(runEvent);
        log.debug(
            "Emitted OpenLineage event: type={}, job={}",
            runEvent.getEventType(),
            runEvent.getJob().getName());
        return; // 成功，直接返回
      } catch (Exception e) {
        lastException = e;
        attempt++;
        if (attempt < retryTotal) {
          // 计算退避时间：backoffFactor * 2^(attempt-1) 秒
          long sleepMs = (long) (retryBackoffFactor * Math.pow(2, attempt - 1) * 1000);
          log.warn(
              "Failed to emit event (attempt {}/{}), retrying in {}ms: {}",
              attempt,
              retryTotal,
              sleepMs,
              e.getMessage());
          try {
            Thread.sleep(sleepMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }

    log.error(
        "Failed to emit OpenLineage event after {} attempts: job={}",
        retryTotal,
        runEvent.getJob().getName(),
        lastException);
  }
}
