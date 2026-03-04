/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/

package org.apache.gravitino.listener.openlineage;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.RunEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.gravitino.listener.api.event.AlterCatalogEvent;
import org.apache.gravitino.listener.api.event.AlterMetricEvent;
import org.apache.gravitino.listener.api.event.AlterModifierEvent;
import org.apache.gravitino.listener.api.event.AlterSchemaEvent;
import org.apache.gravitino.listener.api.event.AlterTableEvent;
import org.apache.gravitino.listener.api.event.AlterTagEvent;
import org.apache.gravitino.listener.api.event.AlterUnitEvent;
import org.apache.gravitino.listener.api.event.AlterValueDomainEvent;
import org.apache.gravitino.listener.api.event.AlterWordRootEvent;
import org.apache.gravitino.listener.api.event.AssociateTagsForMetadataObjectEvent;
import org.apache.gravitino.listener.api.event.CreateCatalogEvent;
import org.apache.gravitino.listener.api.event.CreateModifierEvent;
import org.apache.gravitino.listener.api.event.CreateSchemaEvent;
import org.apache.gravitino.listener.api.event.CreateTableEvent;
import org.apache.gravitino.listener.api.event.CreateTagEvent;
import org.apache.gravitino.listener.api.event.CreateUnitEvent;
import org.apache.gravitino.listener.api.event.CreateValueDomainEvent;
import org.apache.gravitino.listener.api.event.CreateWordRootEvent;
import org.apache.gravitino.listener.api.event.DeleteTagEvent;
import org.apache.gravitino.listener.api.event.DropCatalogEvent;
import org.apache.gravitino.listener.api.event.DropMetricEvent;
import org.apache.gravitino.listener.api.event.DropModifierEvent;
import org.apache.gravitino.listener.api.event.DropSchemaEvent;
import org.apache.gravitino.listener.api.event.DropTableEvent;
import org.apache.gravitino.listener.api.event.DropUnitEvent;
import org.apache.gravitino.listener.api.event.DropValueDomainEvent;
import org.apache.gravitino.listener.api.event.DropWordRootEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.LoadSchemaEvent;
import org.apache.gravitino.listener.api.event.LoadTableEvent;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.openlineage.converters.CatalogEventConverter;
import org.apache.gravitino.listener.openlineage.converters.MetricEventConverter;
import org.apache.gravitino.listener.openlineage.converters.ModifierEventConverter;
import org.apache.gravitino.listener.openlineage.converters.SchemaEventConverter;
import org.apache.gravitino.listener.openlineage.converters.TableEventConverter;
import org.apache.gravitino.listener.openlineage.converters.TagEventConverter;
import org.apache.gravitino.listener.openlineage.converters.UnitEventConverter;
import org.apache.gravitino.listener.openlineage.converters.ValueDomainEventConverter;
import org.apache.gravitino.listener.openlineage.converters.WordRootEventConverter;

/**
 * Gravitino event converter(Delegate mode).*
 *
 * <p>Delegate different types of events to corresponding sub-converters for processing:*
 *
 * <ul>
 *   <li>TableEventConverter:Table Related events
 *   <li>SchemaEventConverter:Schema Related events
 *   <li>CatalogEventConverter:Catalog Related events
 *   <li>MetricEventConverter:Metric Related events
 *   <li>TagEventConverter:Tag Related events
 *   <li>WordRootEventConverter:WordRoot Related events
 *   <li>ModifierEventConverter:Modifier Related events
 *   <li>UnitEventConverter:Unit Related events
 *   <li>ValueDomainEventConverter:ValueDomain Related events
 * </ul>
 */
@Slf4j
public class GravitinoEventConverter {

  private final TableEventConverter tableConverter;
  private final SchemaEventConverter schemaConverter;
  private final CatalogEventConverter catalogConverter;
  private final MetricEventConverter metricConverter;
  private final TagEventConverter tagConverter;
  private final WordRootEventConverter wordRootConverter;
  private final ModifierEventConverter modifierConverter;
  private final UnitEventConverter unitConverter;
  private final ValueDomainEventConverter valueDomainConverter;

  public GravitinoEventConverter(OpenLineage openLineage, String namespace) {
    this.tableConverter = new TableEventConverter(openLineage, namespace);
    this.schemaConverter = new SchemaEventConverter(openLineage, namespace);
    this.catalogConverter = new CatalogEventConverter(openLineage, namespace);
    this.metricConverter = new MetricEventConverter(openLineage, namespace);
    this.tagConverter = new TagEventConverter(openLineage, namespace);
    this.wordRootConverter = new WordRootEventConverter(openLineage, namespace);
    this.modifierConverter = new ModifierEventConverter(openLineage, namespace);
    this.unitConverter = new UnitEventConverter(openLineage, namespace);
    this.valueDomainConverter = new ValueDomainEventConverter(openLineage, namespace);
  }

  /**
   * will Gravitino event converted to OpenLineage RunEvent.*
   *
   * @param event Gravitino event
   * @return OpenLineage RunEvent,Returns if the event type is not supported null
   */
  public RunEvent convert(Event event) {
    // Table event
    if (event instanceof CreateTableEvent
        || event instanceof AlterTableEvent
        || event instanceof DropTableEvent
        || event instanceof LoadTableEvent) {
      return tableConverter.convert(event);
    }

    // Schema event
    if (event instanceof CreateSchemaEvent
        || event instanceof AlterSchemaEvent
        || event instanceof DropSchemaEvent
        || event instanceof LoadSchemaEvent) {
      return schemaConverter.convert(event);
    }

    // Catalog event
    if (event instanceof CreateCatalogEvent
        || event instanceof AlterCatalogEvent
        || event instanceof DropCatalogEvent) {
      return catalogConverter.convert(event);
    }

    // Metric event
    if (event instanceof RegisterMetricEvent
        || event instanceof AlterMetricEvent
        || event instanceof DropMetricEvent) {
      return metricConverter.convert(event);
    }

    // Tag event
    if (event instanceof CreateTagEvent
        || event instanceof AlterTagEvent
        || event instanceof DeleteTagEvent
        || event instanceof AssociateTagsForMetadataObjectEvent) {
      return tagConverter.convert(event);
    }

    // WordRoot event
    if (event instanceof CreateWordRootEvent
        || event instanceof AlterWordRootEvent
        || event instanceof DropWordRootEvent) {
      return wordRootConverter.convert(event);
    }

    // Modifier event
    if (event instanceof CreateModifierEvent
        || event instanceof AlterModifierEvent
        || event instanceof DropModifierEvent) {
      return modifierConverter.convert(event);
    }

    // Unit event
    if (event instanceof CreateUnitEvent
        || event instanceof AlterUnitEvent
        || event instanceof DropUnitEvent) {
      return unitConverter.convert(event);
    }

    // ValueDomain event
    if (event instanceof CreateValueDomainEvent
        || event instanceof AlterValueDomainEvent
        || event instanceof DropValueDomainEvent) {
      return valueDomainConverter.convert(event);
    }

    log.debug("Unsupported event type:{}", event.getClass().getSimpleName());
    return null;
  }
}
