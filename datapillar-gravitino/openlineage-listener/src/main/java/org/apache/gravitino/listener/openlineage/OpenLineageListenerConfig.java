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

import java.net.URI;

/**
 * Configuration constants for OpenLineage Gravitino Listener.
 *
 * <p>配置键遵循 OpenLineage Java Client 标准格式，前缀为 transport.
 *
 * <p>示例配置（gravitino.conf）：
 *
 * <pre>
 * gravitino.openlineage.transport.type = http
 * gravitino.openlineage.transport.url = http://127.0.0.1:7000
 * gravitino.openlineage.transport.endpoint = /api/openlineage
 * gravitino.openlineage.transport.timeoutInMillis = 5000
 * gravitino.openlineage.transport.compression = gzip
 * gravitino.openlineage.transport.auth.type = api_key
 * gravitino.openlineage.transport.auth.apiKey = your-api-key
 * </pre>
 */
public final class OpenLineageListenerConfig {

  private OpenLineageListenerConfig() {}

  /** Configuration key prefix for transport settings. */
  public static final String TRANSPORT_PREFIX = "transport.";

  /** Namespace for OpenLineage events. */
  public static final String NAMESPACE = "namespace";

  /** Default namespace. */
  public static final String DEFAULT_NAMESPACE = "gravitino";

  /** Producer URI for OpenLineage events. */
  public static final URI PRODUCER_URI =
      URI.create("https://github.com/apache/gravitino/openlineage-listener");

  /** Transport type configuration key (console, http). */
  public static final String TRANSPORT_TYPE = "transport.type";

  /** Default transport type. */
  public static final String DEFAULT_TRANSPORT_TYPE = "console";

  // HTTP Transport 配置键
  /** HTTP transport URL. */
  public static final String TRANSPORT_URL = "transport.url";

  /** HTTP transport endpoint (default: /api/openlineage). */
  public static final String TRANSPORT_ENDPOINT = "transport.endpoint";

  /** Default endpoint. */
  public static final String DEFAULT_ENDPOINT = "/api/openlineage";

  /** HTTP transport timeout in milliseconds (default: 5000). */
  public static final String TRANSPORT_TIMEOUT = "transport.timeoutInMillis";

  /** Default timeout. */
  public static final int DEFAULT_TIMEOUT = 5000;

  /** HTTP transport compression (none, gzip). */
  public static final String TRANSPORT_COMPRESSION = "transport.compression";

  // Auth 配置键
  /** Auth type (api_key). */
  public static final String TRANSPORT_AUTH_TYPE = "transport.auth.type";

  /** API Key for authentication. */
  public static final String TRANSPORT_AUTH_API_KEY = "transport.auth.apiKey";

  // Headers 配置键前缀
  /** Custom headers prefix (e.g., transport.headers.X-Tenant-Id). */
  public static final String TRANSPORT_HEADERS_PREFIX = "transport.headers.";

  // 异步队列配置（限流）
  /** Maximum events in processing queue (default: 10000). */
  public static final String TRANSPORT_MAX_QUEUE_SIZE = "transport.maxQueueSize";

  /** Default max queue size. */
  public static final int DEFAULT_MAX_QUEUE_SIZE = 10000;

  /** Maximum parallel HTTP requests (default: 100). */
  public static final String TRANSPORT_MAX_CONCURRENT_REQUESTS = "transport.maxConcurrentRequests";

  /** Default max concurrent requests. */
  public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 100;

  // Retry 配置
  /** Total number of retries (default: 5). */
  public static final String TRANSPORT_RETRY_TOTAL = "transport.retry.total";

  /** Default retry total. */
  public static final int DEFAULT_RETRY_TOTAL = 5;

  /** Backoff factor for retries (default: 0.3). */
  public static final String TRANSPORT_RETRY_BACKOFF_FACTOR = "transport.retry.backoffFactor";

  /** Default backoff factor. */
  public static final double DEFAULT_RETRY_BACKOFF_FACTOR = 0.3;
}
