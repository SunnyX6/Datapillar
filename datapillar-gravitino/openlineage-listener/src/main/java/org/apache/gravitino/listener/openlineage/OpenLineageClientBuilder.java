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

import io.openlineage.client.OpenLineageClient;
import io.openlineage.client.transports.ApiKeyTokenProvider;
import io.openlineage.client.transports.ConsoleTransport;
import io.openlineage.client.transports.HttpConfig;
import io.openlineage.client.transports.HttpTransport;
import io.openlineage.client.transports.Transport;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Builder for OpenLineage client with configurable transport.
 *
 * <p>supported transport Type：
 *
 * <ul>
 *   <li>console - Log output（Default，for debugging）
 *   <li>http - send to HTTP endpoint（Recommended for production environment）
 * </ul>
 *
 * <p>Supported configurations：
 *
 * <ul>
 *   <li>maxQueueSize - Maximum queue size（Current limiting）
 *   <li>maxConcurrentRequests - Maximum number of concurrent requests
 *   <li>retry.total - Number of retries
 *   <li>retry.backoffFactor - Retry backoff factor
 * </ul>
 */
@Slf4j
public class OpenLineageClientBuilder {

  private final Map<String, String> properties;

  @Getter private int maxQueueSize;
  @Getter private int maxConcurrentRequests;
  @Getter private int retryTotal;
  @Getter private double retryBackoffFactor;

  private ExecutorService executorService;
  private Semaphore concurrencySemaphore;

  public OpenLineageClientBuilder(Map<String, String> properties) {
    this.properties = properties;
    parseConfig();
  }

  private void parseConfig() {
    this.maxQueueSize =
        getIntProperty(
            OpenLineageListenerConfig.TRANSPORT_MAX_QUEUE_SIZE,
            OpenLineageListenerConfig.DEFAULT_MAX_QUEUE_SIZE);

    this.maxConcurrentRequests =
        getIntProperty(
            OpenLineageListenerConfig.TRANSPORT_MAX_CONCURRENT_REQUESTS,
            OpenLineageListenerConfig.DEFAULT_MAX_CONCURRENT_REQUESTS);

    this.retryTotal =
        getIntProperty(
            OpenLineageListenerConfig.TRANSPORT_RETRY_TOTAL,
            OpenLineageListenerConfig.DEFAULT_RETRY_TOTAL);

    this.retryBackoffFactor =
        getDoubleProperty(
            OpenLineageListenerConfig.TRANSPORT_RETRY_BACKOFF_FACTOR,
            OpenLineageListenerConfig.DEFAULT_RETRY_BACKOFF_FACTOR);

    log.info(
        "OpenLineage config: maxQueueSize={}, maxConcurrentRequests={}, retryTotal={}, backoffFactor={}",
        maxQueueSize,
        maxConcurrentRequests,
        retryTotal,
        retryBackoffFactor);
  }

  private int getIntProperty(String key, int defaultValue) {
    String value = properties.get(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        log.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
      }
    }
    return defaultValue;
  }

  private double getDoubleProperty(String key, double defaultValue) {
    String value = properties.get(key);
    if (value != null) {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException e) {
        log.warn("Invalid double value for {}: {}, using default: {}", key, value, defaultValue);
      }
    }
    return defaultValue;
  }

  /** Build OpenLineage client with transport from properties. */
  public OpenLineageClient build() {
    String transportType =
        properties.getOrDefault(
            OpenLineageListenerConfig.TRANSPORT_TYPE,
            OpenLineageListenerConfig.DEFAULT_TRANSPORT_TYPE);

    Transport transport = createTransport(transportType);

    // Initialize asynchronous executors and concurrency control
    initAsyncExecutor();

    return OpenLineageClient.builder().transport(transport).build();
  }

  private void initAsyncExecutor() {
    this.concurrencySemaphore = new Semaphore(maxConcurrentRequests);
    this.executorService =
        new ThreadPoolExecutor(
            maxConcurrentRequests,
            maxConcurrentRequests,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(maxQueueSize),
            new ThreadPoolExecutor.CallerRunsPolicy());

    log.info(
        "Initialized async executor: maxConcurrentRequests={}, maxQueueSize={}",
        maxConcurrentRequests,
        maxQueueSize);
  }

  /** Get the executor service（for asynchronous sending） */
  public ExecutorService getExecutorService() {
    return executorService;
  }

  /** Get concurrent semaphore（used to limit current） */
  public Semaphore getConcurrencySemaphore() {
    return concurrencySemaphore;
  }

  /** Close resource */
  public void shutdown() {
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private Transport createTransport(String type) {
    if ("console".equalsIgnoreCase(type)) {
      log.info("Using console transport for OpenLineage events");
      return new ConsoleTransport();
    }

    if ("http".equalsIgnoreCase(type)) {
      return createHttpTransport();
    }

    log.warn("Unknown transport type '{}', falling back to console transport", type);
    return new ConsoleTransport();
  }

  private Transport createHttpTransport() {
    String url = properties.get(OpenLineageListenerConfig.TRANSPORT_URL);
    if (url == null || url.isEmpty()) {
      log.warn("HTTP transport URL not configured, falling back to console transport");
      return new ConsoleTransport();
    }

    try {
      HttpConfig httpConfig = new HttpConfig();
      httpConfig.setUrl(URI.create(url));

      // settings endpoint
      String endpoint =
          properties.getOrDefault(
              OpenLineageListenerConfig.TRANSPORT_ENDPOINT,
              OpenLineageListenerConfig.DEFAULT_ENDPOINT);
      httpConfig.setEndpoint(endpoint);

      // settings timeout
      String timeoutStr = properties.get(OpenLineageListenerConfig.TRANSPORT_TIMEOUT);
      if (timeoutStr != null) {
        httpConfig.setTimeoutInMillis(Integer.valueOf(timeoutStr));
      } else {
        httpConfig.setTimeoutInMillis(OpenLineageListenerConfig.DEFAULT_TIMEOUT);
      }

      // settings compression
      String compression = properties.get(OpenLineageListenerConfig.TRANSPORT_COMPRESSION);
      if ("gzip".equalsIgnoreCase(compression)) {
        httpConfig.setCompression(HttpConfig.Compression.GZIP);
      }

      // settings auth
      String authType = properties.get(OpenLineageListenerConfig.TRANSPORT_AUTH_TYPE);
      if ("api_key".equalsIgnoreCase(authType)) {
        String apiKey = properties.get(OpenLineageListenerConfig.TRANSPORT_AUTH_API_KEY);
        if (apiKey != null && !apiKey.isEmpty()) {
          ApiKeyTokenProvider tokenProvider = new ApiKeyTokenProvider();
          tokenProvider.setApiKey(apiKey);
          httpConfig.setAuth(tokenProvider);
        }
      }

      // settings custom headers
      Map<String, String> headers = extractHeaders();
      if (!headers.isEmpty()) {
        httpConfig.setHeaders(headers);
      }

      log.info("Using HTTP transport for OpenLineage events, URL: {}, endpoint: {}", url, endpoint);
      return new HttpTransport(httpConfig);
    } catch (Exception e) {
      log.warn("Failed to create HTTP transport, falling back to console: {}", e.getMessage());
      return new ConsoleTransport();
    }
  }

  /** Extract with transport.headers. Customization of the prefix headers */
  private Map<String, String> extractHeaders() {
    Map<String, String> headers = new HashMap<>();
    String prefix = OpenLineageListenerConfig.TRANSPORT_HEADERS_PREFIX;

    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (entry.getKey().startsWith(prefix)) {
        String headerName = entry.getKey().substring(prefix.length());
        headers.put(headerName, entry.getValue());
      }
    }
    return headers;
  }
}
