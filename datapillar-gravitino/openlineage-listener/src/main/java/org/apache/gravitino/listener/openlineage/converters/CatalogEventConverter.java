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
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent;
import java.util.Collections;
import org.apache.gravitino.Audit;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterCatalogEvent;
import org.apache.gravitino.listener.api.event.CreateCatalogEvent;
import org.apache.gravitino.listener.api.event.DropCatalogEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.info.CatalogInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;

/**
 * Catalog 事件转换器。
 *
 * <p>处理: CreateCatalogEvent, AlterCatalogEvent, DropCatalogEvent
 */
public class CatalogEventConverter extends BaseEventConverter {

  private static final String GRAVITINO_FACET_KEY = "gravitino";

  public CatalogEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateCatalogEvent) {
      return convertCreateCatalog((CreateCatalogEvent) event);
    } else if (event instanceof AlterCatalogEvent) {
      return convertAlterCatalog((AlterCatalogEvent) event);
    } else if (event instanceof DropCatalogEvent) {
      return convertDropCatalog((DropCatalogEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateCatalog(CreateCatalogEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
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
        "gravitino.create_catalog",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterCatalog(AlterCatalogEvent event) {
    NameIdentifier identifier = event.identifier();
    CatalogInfo catalogInfo = event.updatedCatalogInfo();

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder facetBuilder =
        tenantFacetBuilder(event)
            .description(catalogInfo.comment())
            .properties(catalogInfo.properties());

    Audit audit = catalogInfo.auditInfo();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(facetBuilder, audit);
    }

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null));
    facetsBuilder.put(GRAVITINO_FACET_KEY, facetBuilder.build());

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_catalog",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropCatalog(DropCatalogEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
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
        "gravitino.drop_catalog",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }
}
