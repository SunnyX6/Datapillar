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
import org.apache.gravitino.listener.api.event.AlterModifierEvent;
import org.apache.gravitino.listener.api.event.CreateModifierEvent;
import org.apache.gravitino.listener.api.event.DropModifierEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.info.ModifierInfo;

/**
 * Modifier 事件转换器。
 *
 * <p>处理: CreateModifierEvent, AlterModifierEvent, DropModifierEvent
 */
public class ModifierEventConverter extends BaseEventConverter {

  public ModifierEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateModifierEvent) {
      return convertCreateModifier((CreateModifierEvent) event);
    } else if (event instanceof AlterModifierEvent) {
      return convertAlterModifier((AlterModifierEvent) event);
    } else if (event instanceof DropModifierEvent) {
      return convertDropModifier((DropModifierEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateModifier(CreateModifierEvent event) {
    NameIdentifier identifier = event.identifier();
    ModifierInfo modifierInfo = event.createdModifierInfo();

    List<SchemaDatasetFacetFields> fields = buildModifierFields(modifierInfo);
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
        "gravitino.create_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterModifier(AlterModifierEvent event) {
    NameIdentifier identifier = event.identifier();
    ModifierInfo modifierInfo = event.updatedModifierInfo();

    List<SchemaDatasetFacetFields> fields = buildModifierFields(modifierInfo);
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
        "gravitino.alter_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropModifier(DropModifierEvent event) {
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
        "gravitino.drop_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private List<SchemaDatasetFacetFields> buildModifierFields(ModifierInfo modifierInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("code", "STRING", modifierInfo.code(), null));
    modifierInfo
        .modifierType()
        .ifPresent(
            t -> fields.add(openLineage.newSchemaDatasetFacetFields("type", "STRING", t, null)));
    modifierInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    return fields;
  }
}
