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
import java.util.stream.Collectors;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterValueDomainEvent;
import org.apache.gravitino.listener.api.event.CreateValueDomainEvent;
import org.apache.gravitino.listener.api.event.DropValueDomainEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.info.ValueDomainInfo;

/**
 * ValueDomain 事件转换器。
 *
 * <p>处理: CreateValueDomainEvent, AlterValueDomainEvent, DropValueDomainEvent
 */
public class ValueDomainEventConverter extends BaseEventConverter {

  public ValueDomainEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateValueDomainEvent) {
      return convertCreateValueDomain((CreateValueDomainEvent) event);
    } else if (event instanceof AlterValueDomainEvent) {
      return convertAlterValueDomain((AlterValueDomainEvent) event);
    } else if (event instanceof DropValueDomainEvent) {
      return convertDropValueDomain((DropValueDomainEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateValueDomain(CreateValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();
    ValueDomainInfo valueDomainInfo = event.createdValueDomainInfo();

    List<SchemaDatasetFacetFields> fields = buildValueDomainFields(valueDomainInfo);
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
        "gravitino.create_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterValueDomain(AlterValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();
    ValueDomainInfo valueDomainInfo = event.updatedValueDomainInfo();

    List<SchemaDatasetFacetFields> fields = buildValueDomainFields(valueDomainInfo);
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
        "gravitino.alter_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropValueDomain(DropValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("domainCode", "STRING", identifier.name(), null));

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
        "gravitino.drop_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private List<SchemaDatasetFacetFields> buildValueDomainFields(ValueDomainInfo valueDomainInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainCode", "STRING", valueDomainInfo.domainCode(), null));
    valueDomainInfo
        .domainName()
        .ifPresent(
            n ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("domainName", "STRING", n, null)));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainType", "STRING", valueDomainInfo.domainType().name(), null));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainLevel", "STRING", valueDomainInfo.domainLevel().name(), null));
    if (valueDomainInfo.items() != null && !valueDomainInfo.items().isEmpty()) {
      String itemsStr =
          valueDomainInfo.items().stream()
              .map(item -> item.value() + ":" + (item.label() != null ? item.label() : ""))
              .collect(Collectors.joining(","));
      fields.add(openLineage.newSchemaDatasetFacetFields("items", "STRING", itemsStr, null));
    }
    valueDomainInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    valueDomainInfo
        .dataType()
        .ifPresent(
            dt ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("dataType", "STRING", dt, null)));
    return fields;
  }
}
