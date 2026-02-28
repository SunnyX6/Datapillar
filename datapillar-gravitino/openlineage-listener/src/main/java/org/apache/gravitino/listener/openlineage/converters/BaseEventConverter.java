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

package org.apache.gravitino.listener.openlineage.converters;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.Job;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.Run;
import io.openlineage.client.OpenLineage.RunEvent;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;

/** 事件转换器基类，提供共享的工具方法。 */
public abstract class BaseEventConverter {

  protected final OpenLineage openLineage;
  protected final String namespace;
  protected final URI producerUri;

  protected BaseEventConverter(OpenLineage openLineage, String namespace) {
    this.openLineage = openLineage;
    this.namespace = namespace;
    this.producerUri = URI.create("https://github.com/apache/gravitino");
  }

  /** 创建 OpenLineage RunEvent。 */
  protected RunEvent createRunEvent(
      Event event,
      String jobName,
      OpenLineage.RunEvent.EventType eventType,
      List<InputDataset> inputs,
      List<OutputDataset> outputs) {

    long tenantId = requiredTenantId(event);
    UUID runId = UUID.randomUUID();
    ZonedDateTime eventTime =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.eventTime()), ZoneOffset.UTC);

    Run run = openLineage.newRunBuilder().runId(runId).build();
    Job job =
        openLineage.newJobBuilder().namespace(formatJobNamespace(tenantId)).name(jobName).build();

    return openLineage
        .newRunEventBuilder()
        .eventType(eventType)
        .eventTime(eventTime)
        .run(run)
        .job(job)
        .inputs(inputs)
        .outputs(outputs)
        .build();
  }

  /**
   * 格式化 dataset namespace。
   *
   * <p>格式: gravitino://tenant/{tenantId}/{metalake}/{catalog}
   */
  protected String formatDatasetNamespace(Event event, NameIdentifier identifier) {
    long tenantId = requiredTenantId(event);
    String[] parts = identifier.namespace().levels();
    if (parts.length >= 2) {
      return String.format("gravitino://tenant/%d/%s/%s", tenantId, parts[0], parts[1]);
    } else if (parts.length == 1) {
      return String.format("gravitino://tenant/%d/%s", tenantId, parts[0]);
    }
    return formatJobNamespace(tenantId);
  }

  /**
   * 格式化 dataset name。
   *
   * <p>格式: {schema}.{table} 或 {name}
   */
  protected String formatDatasetName(NameIdentifier identifier) {
    String[] parts = identifier.namespace().levels();
    if (parts.length >= 3) {
      // metalake.catalog.schema.table -> schema.table
      return parts[2] + "." + identifier.name();
    }
    return identifier.name();
  }

  protected String formatJobNamespace(long tenantId) {
    return String.format("gravitino://tenant/%d", tenantId);
  }

  protected GravitinoDatasetFacet.GravitinoDatasetFacetBuilder tenantFacetBuilder(Event event) {
    long tenantId = requiredTenantId(event);
    return GravitinoDatasetFacet.builder(producerUri)
        .tenantId(tenantId)
        .tenantCode(event.tenantCode())
        .tenantName(event.tenantName());
  }

  private long requiredTenantId(Event event) {
    if (event.tenantId() == null || event.tenantId() <= 0) {
      throw new IllegalArgumentException(
          String.format(
              "OpenLineage event %s missing tenant_id snapshot", event.getClass().getSimpleName()));
    }
    return event.tenantId();
  }
}
