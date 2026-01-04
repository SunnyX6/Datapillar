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
import io.openlineage.client.OpenLineage.DatasetFacets;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineage.SchemaDatasetFacet;
import io.openlineage.client.OpenLineage.SchemaDatasetFacetFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterMetricEvent;
import org.apache.gravitino.listener.api.event.DropMetricEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.api.info.MetricInfo;

/**
 * Metric 事件转换器。
 *
 * <p>处理: RegisterMetricEvent, AlterMetricEvent, DropMetricEvent
 */
public class MetricEventConverter extends BaseEventConverter {

  public MetricEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof RegisterMetricEvent) {
      return convertRegisterMetric((RegisterMetricEvent) event);
    } else if (event instanceof AlterMetricEvent) {
      return convertAlterMetric((AlterMetricEvent) event);
    } else if (event instanceof DropMetricEvent) {
      return convertDropMetric((DropMetricEvent) event);
    }
    return null;
  }

  private RunEvent convertRegisterMetric(RegisterMetricEvent event) {
    NameIdentifier identifier = event.identifier();
    MetricInfo metricInfo = event.registeredMetricInfo();

    List<SchemaDatasetFacetFields> fields = buildMetricFields(metricInfo);
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.CREATE, null))
            .build();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.register_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterMetric(AlterMetricEvent event) {
    NameIdentifier identifier = event.identifier();
    MetricInfo metricInfo = event.updatedMetricInfo();

    List<SchemaDatasetFacetFields> fields = buildMetricFields(metricInfo);
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
            .build();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropMetric(DropMetricEvent event) {
    NameIdentifier identifier = event.identifier();

    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private List<SchemaDatasetFacetFields> buildMetricFields(MetricInfo metricInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", metricInfo.code(), null));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "type", "STRING", metricInfo.metricType().name(), null));
    metricInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    metricInfo
        .unit()
        .ifPresent(
            u -> fields.add(openLineage.newSchemaDatasetFacetFields("unit", "STRING", u, null)));
    metricInfo
        .calculationFormula()
        .ifPresent(
            f ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields(
                        "calculationFormula", "STRING", f, null)));

    String[] parentCodes = metricInfo.parentMetricCodes();
    if (parentCodes != null && parentCodes.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parentCodes.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(parentCodes[i]);
      }
      fields.add(
          openLineage.newSchemaDatasetFacetFields(
              "parentMetricCodes", "STRING", sb.toString(), null));
    }

    metricInfo
        .refCatalogName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refCatalogName", "STRING", v, null)));
    metricInfo
        .refSchemaName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refSchemaName", "STRING", v, null)));
    metricInfo
        .refTableName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refTableName", "STRING", v, null)));
    metricInfo
        .measureColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("measureColumns", "JSON", v, null)));
    metricInfo
        .filterColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("filterColumns", "JSON", v, null)));

    return fields;
  }
}
