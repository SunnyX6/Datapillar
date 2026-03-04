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

package org.apache.gravitino.listener.openlineage.converters;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.DatasetFacets;
import io.openlineage.client.OpenLineage.DatasetFacetsBuilder;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineage.SchemaDatasetFacet;
import io.openlineage.client.OpenLineage.SchemaDatasetFacetFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterTagEvent;
import org.apache.gravitino.listener.api.event.AssociateTagsForMetadataObjectEvent;
import org.apache.gravitino.listener.api.event.CreateTagEvent;
import org.apache.gravitino.listener.api.event.DeleteTagEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.info.TagInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoTagFacet;

/**
 * Tag event converter.*
 *
 * <p>Process:CreateTagEvent,AlterTagEvent,DeleteTagEvent,AssociateTagsForMetadataObjectEvent
 */
public class TagEventConverter extends BaseEventConverter {

  public TagEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateTagEvent) {
      return convertCreateTag((CreateTagEvent) event);
    } else if (event instanceof AlterTagEvent) {
      return convertAlterTag((AlterTagEvent) event);
    } else if (event instanceof DeleteTagEvent) {
      return convertDropTag((DeleteTagEvent) event);
    } else if (event instanceof AssociateTagsForMetadataObjectEvent) {
      return convertAssociateTagsForMetadataObject((AssociateTagsForMetadataObjectEvent) event);
    }
    return null;
  }

  /**
   * Convert CreateTagEvent(create Tag metadata).*
   *
   * <p>Tag as a standalone metadata object,namespace The format is gravitino://{metalake},name for
   * tagName.
   */
  private RunEvent convertCreateTag(CreateTagEvent event) {
    NameIdentifier identifier = event.identifier();
    TagInfo tagInfo = event.createdTagInfo();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", tagInfo.name(), null));
    if (tagInfo.comment() != null) {
      fields.add(
          openLineage.newSchemaDatasetFacetFields("comment", "STRING", tagInfo.comment(), null));
    }
    if (tagInfo.properties() != null && !tagInfo.properties().isEmpty()) {
      String propsStr =
          tagInfo.properties().entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining(","));
      fields.add(openLineage.newSchemaDatasetFacetFields("properties", "STRING", propsStr, null));
    }

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
            .namespace(formatTagMetadataNamespace(identifier))
            .name(identifier.name())
            .facets(facets)
            .build();
    return createRunEvent(
        event,
        "gravitino.create_tag",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /** Convert AlterTagEvent(Modify Tag metadata). */
  private RunEvent convertAlterTag(AlterTagEvent event) {
    NameIdentifier identifier = event.identifier();
    TagInfo tagInfo = event.updatedTagInfo();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", tagInfo.name(), null));
    if (tagInfo.comment() != null) {
      fields.add(
          openLineage.newSchemaDatasetFacetFields("comment", "STRING", tagInfo.comment(), null));
    }
    if (tagInfo.properties() != null && !tagInfo.properties().isEmpty()) {
      String propsStr =
          tagInfo.properties().entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining(","));
      fields.add(openLineage.newSchemaDatasetFacetFields("properties", "STRING", propsStr, null));
    }

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
            .namespace(formatTagMetadataNamespace(identifier))
            .name(identifier.name())
            .facets(facets)
            .build();
    return createRunEvent(
        event,
        "gravitino.alter_tag",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * Convert DeleteTagEvent(Delete Tag metadata).*
   *
   * <p>Note:Gravitino Native event class name DeleteTagEvent,But Job The name is unified as
   * drop_tag.
   */
  private RunEvent convertDropTag(DeleteTagEvent event) {
    NameIdentifier identifier = event.identifier();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", identifier.name(), null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);
    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatTagMetadataNamespace(identifier))
            .name(identifier.name())
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
        "gravitino.drop_tag",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * Convert AssociateTagsForMetadataObjectEvent(association Tag to metadata object).*
   *
   * <p>will Tag associated information as custom facet passed to OpenLineage.
   */
  private RunEvent convertAssociateTagsForMetadataObject(
      AssociateTagsForMetadataObjectEvent event) {
    NameIdentifier identifier = event.identifier();
    MetadataObject.Type objectType = event.objectType();
    String datasetNamespace = formatTagDatasetNamespace(identifier, objectType);
    String datasetName = formatTagDatasetName(identifier, objectType);
    GravitinoTagFacet tagFacet =
        GravitinoTagFacet.builder(producerUri)
            .objectType(objectType.name())
            .tagsToAdd(event.tagsToAdd())
            .tagsToRemove(event.tagsToRemove())
            .associatedTags(event.associatedTags())
            .build();
    DatasetFacetsBuilder facetsBuilder = openLineage.newDatasetFacetsBuilder();
    facetsBuilder.put("gravitinoTag", tagFacet);
    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(datasetNamespace)
            .name(datasetName)
            .facets(facetsBuilder.build())
            .build();
    return createRunEvent(
        event,
        "gravitino.associate_tags",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * Format Tag metadata event dataset namespace.*
   *
   * <p>Tag of identifier:namespace.levels = [metalake],name = tagName
   *
   * <p>Format:gravitino://{metalake}
   */
  private String formatTagMetadataNamespace(NameIdentifier identifier) {
    String[] parts = identifier.namespace().levels();
    if (parts.length >= 1) {
      return String.format("gravitino://%s", parts[0]);
    }
    return namespace;
  }

  /** Format based on object type Tag related to events dataset namespace. */
  private String formatTagDatasetNamespace(NameIdentifier identifier, MetadataObject.Type type) {
    String[] parts = identifier.namespace().levels();
    switch (type) {
      case CATALOG:
        if (parts.length >= 1) {
          return String.format("gravitino://%s", parts[0]);
        }
        break;
      case SCHEMA:
        if (parts.length >= 2) {
          return String.format("gravitino://%s/%s", parts[0], parts[1]);
        }
        break;
      case TABLE:
      case COLUMN:
        if (parts.length >= 2) {
          return String.format("gravitino://%s/%s", parts[0], parts[1]);
        }
        break;
      default:
        break;
    }
    return namespace;
  }

  /** Format based on object type Tag related to events dataset name. */
  private String formatTagDatasetName(NameIdentifier identifier, MetadataObject.Type type) {
    String[] parts = identifier.namespace().levels();
    String name = identifier.name();
    switch (type) {
      case CATALOG:
        return name;
      case SCHEMA:
        return name;
      case TABLE:
        if (parts.length >= 3) {
          return parts[2] + "." + name;
        }
        return name;
      case COLUMN:
        if (parts.length >= 4) {
          return parts[2] + "." + parts[3] + "." + name;
        }
        return name;
      default:
        return name;
    }
  }
}
