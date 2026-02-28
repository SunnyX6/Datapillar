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
import org.apache.gravitino.listener.api.event.AlterUnitEvent;
import org.apache.gravitino.listener.api.event.CreateUnitEvent;
import org.apache.gravitino.listener.api.event.DropUnitEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.info.UnitInfo;

/**
 * Unit 事件转换器。
 *
 * <p>处理: CreateUnitEvent, AlterUnitEvent, DropUnitEvent
 */
public class UnitEventConverter extends BaseEventConverter {

  public UnitEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateUnitEvent) {
      return convertCreateUnit((CreateUnitEvent) event);
    } else if (event instanceof AlterUnitEvent) {
      return convertAlterUnit((AlterUnitEvent) event);
    } else if (event instanceof DropUnitEvent) {
      return convertDropUnit((DropUnitEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateUnit(CreateUnitEvent event) {
    NameIdentifier identifier = event.identifier();
    UnitInfo unitInfo = event.createdUnitInfo();

    List<SchemaDatasetFacetFields> fields = buildUnitFields(unitInfo);
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
            .namespace(formatDatasetNamespace(event, identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.create_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterUnit(AlterUnitEvent event) {
    NameIdentifier identifier = event.identifier();
    UnitInfo unitInfo = event.updatedUnitInfo();

    List<SchemaDatasetFacetFields> fields = buildUnitFields(unitInfo);
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
            .namespace(formatDatasetNamespace(event, identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropUnit(DropUnitEvent event) {
    NameIdentifier identifier = event.identifier();

    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(event, identifier))
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
        "gravitino.drop_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private List<SchemaDatasetFacetFields> buildUnitFields(UnitInfo unitInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", unitInfo.code(), null));
    unitInfo
        .name()
        .ifPresent(
            n -> fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", n, null)));
    unitInfo
        .symbol()
        .ifPresent(
            s -> fields.add(openLineage.newSchemaDatasetFacetFields("symbol", "STRING", s, null)));
    unitInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    return fields;
  }
}
