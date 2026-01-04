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
import io.openlineage.client.OpenLineage.DatasetFacetsBuilder;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent;
import java.util.Collections;
import org.apache.gravitino.Audit;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterSchemaEvent;
import org.apache.gravitino.listener.api.event.CreateSchemaEvent;
import org.apache.gravitino.listener.api.event.DropSchemaEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.LoadSchemaEvent;
import org.apache.gravitino.listener.api.info.SchemaInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;

/**
 * Schema 事件转换器。
 *
 * <p>处理: CreateSchemaEvent, AlterSchemaEvent, DropSchemaEvent, LoadSchemaEvent
 */
public class SchemaEventConverter extends BaseEventConverter {

  private static final String GRAVITINO_FACET_KEY = "gravitino";

  public SchemaEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateSchemaEvent) {
      return convertCreateSchema((CreateSchemaEvent) event);
    } else if (event instanceof AlterSchemaEvent) {
      return convertAlterSchema((AlterSchemaEvent) event);
    } else if (event instanceof DropSchemaEvent) {
      return convertDropSchema((DropSchemaEvent) event);
    } else if (event instanceof LoadSchemaEvent) {
      return convertLoadSchema((LoadSchemaEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateSchema(CreateSchemaEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange
                                .CREATE,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.create_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterSchema(AlterSchemaEvent event) {
    NameIdentifier identifier = event.identifier();
    SchemaInfo schemaInfo = event.updatedSchemaInfo();

    GravitinoDatasetFacet gravitinoFacet = buildSchemaGravitinoFacet(schemaInfo);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .documentation(
                openLineage.newDocumentationDatasetFacet(
                    schemaInfo != null ? schemaInfo.comment() : null))
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null));

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropSchema(DropSchemaEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertLoadSchema(LoadSchemaEvent event) {
    NameIdentifier identifier = event.identifier();
    SchemaInfo schemaInfo = event.loadedSchemaInfo();

    GravitinoDatasetFacet gravitinoFacet = buildSchemaGravitinoFacet(schemaInfo);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .documentation(
                openLineage.newDocumentationDatasetFacet(
                    schemaInfo != null ? schemaInfo.comment() : null));

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    InputDataset inputDataset =
        openLineage
            .newInputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.load_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.singletonList(inputDataset),
        Collections.emptyList());
  }

  private GravitinoDatasetFacet buildSchemaGravitinoFacet(SchemaInfo schemaInfo) {
    if (schemaInfo == null) {
      return null;
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        GravitinoDatasetFacet.builder(producerUri)
            .description(schemaInfo.comment())
            .properties(schemaInfo.properties());

    Audit audit = schemaInfo.audit();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(builder, audit);
    }

    return builder.build();
  }
}
