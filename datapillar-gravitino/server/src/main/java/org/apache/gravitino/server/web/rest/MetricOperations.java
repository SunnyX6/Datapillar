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
package org.apache.gravitino.server.web.rest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.apache.gravitino.Entity;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.catalog.DatasetDispatcher;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dto.dataset.MetricDTO;
import org.apache.gravitino.dto.dataset.MetricModifierDTO;
import org.apache.gravitino.dto.requests.MetricModifierCreateRequest;
import org.apache.gravitino.dto.requests.MetricModifierUpdateRequest;
import org.apache.gravitino.dto.requests.MetricRegisterRequest;
import org.apache.gravitino.dto.requests.MetricUpdateRequest;
import org.apache.gravitino.dto.requests.MetricUpdatesRequest;
import org.apache.gravitino.dto.requests.MetricVersionUpdateRequest;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.MetricListResponse;
import org.apache.gravitino.dto.responses.MetricModifierListResponse;
import org.apache.gravitino.dto.responses.MetricModifierResponse;
import org.apache.gravitino.dto.responses.MetricResponse;
import org.apache.gravitino.dto.responses.MetricVersionListResponse;
import org.apache.gravitino.dto.responses.MetricVersionResponse;
import org.apache.gravitino.dto.util.DTOConverters;
import org.apache.gravitino.metrics.MetricNames;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.server.authorization.annotations.AuthorizationExpression;
import org.apache.gravitino.server.authorization.annotations.AuthorizationMetadata;
import org.apache.gravitino.server.authorization.expression.AuthorizationExpressionConstants;
import org.apache.gravitino.server.web.Utils;

@Tag(name = "Metric Management", description = "Indicator management relatedAPI")
@Path("metalakes/{metalake}/catalogs/{catalog}/schemas/{schema}/metrics")
public class MetricOperations {

  private final DatasetDispatcher datasetDispatcher;

  @Context private HttpServletRequest httpRequest;

  @Inject
  public MetricOperations(DatasetDispatcher datasetDispatcher) {
    this.datasetDispatcher = datasetDispatcher;
  }

  @GET
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "list-metric." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-metric", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  @Operation(
      summary = "List all indicators",
      description = "list specifiedschemaAll indicators under")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully returns the indicator list"),
        @ApiResponse(responseCode = "404", description = "Schemadoes not exist")
      })
  public Response listMetrics(
      @Parameter(description = "MetalakeName", required = true)
          @PathParam("metalake")
          @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @Parameter(description = "CatalogName", required = true)
          @PathParam("catalog")
          @AuthorizationMetadata(type = Entity.EntityType.CATALOG)
          String catalog,
      @Parameter(description = "SchemaName", required = true)
          @PathParam("schema")
          @AuthorizationMetadata(type = Entity.EntityType.SCHEMA)
          String schema,
      @Parameter(description = "offset") @QueryParam("offset") @DefaultValue("0") int offset,
      @Parameter(description = "page size") @QueryParam("limit") @DefaultValue("20") int limit) {
    Namespace metricNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            PagedResult<Metric> result = datasetDispatcher.listMetrics(metricNs, offset, limit);
            MetricDTO[] metricDTOs =
                result.items().stream().map(DTOConverters::toDTO).toArray(MetricDTO[]::new);
            return Utils.ok(new MetricListResponse(metricDTOs, result.total(), offset, limit));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.LIST, "", schema, e);
    }
  }

  @GET
  @Path("{metric}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "get-metric." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "get-metric", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response getMetric(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            Metric m = datasetDispatcher.getMetric(metricId);
            return Utils.ok(new MetricResponse(DTOConverters.toDTO(m)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.GET, metric, schema, e);
    }
  }

  @POST
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "register-metric." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "register-metric", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response registerMetric(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      MetricRegisterRequest request) {

    try {
      request.validate();
      // use code as identifier，Because the database layer uses code Query
      NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, request.getCode());

      return Utils.doAs(
          httpRequest,
          () -> {
            Metric m =
                datasetDispatcher.registerMetric(
                    metricId,
                    request.getName(),
                    request.getCode(),
                    request.getType(),
                    request.getDataType(),
                    request.getComment(),
                    request.getProperties(),
                    request.getUnit(),
                    request.getParentMetricCodes(),
                    request.getCalculationFormula(),
                    request.getRefTableId(),
                    request.getRefCatalogName(),
                    request.getRefSchemaName(),
                    request.getRefTableName(),
                    request.getMeasureColumnIds(),
                    request.getFilterColumnIds());
            return Utils.ok(new MetricResponse(DTOConverters.toDTO(m)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.REGISTER, request.getName(), schema, e);
    }
  }

  @DELETE
  @Path("{metric}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "delete-metric." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "delete-metric", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response deleteMetric(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            boolean deleted = datasetDispatcher.deleteMetric(metricId);
            return Utils.ok(new DropResponse(deleted));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.DELETE, metric, schema, e);
    }
  }

  @PUT
  @Path("{metric}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "alter-metric." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "alter-metric", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response alterMetric(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric,
      MetricUpdatesRequest request) {
    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            request.validate();
            NameIdentifier ident = NameIdentifier.of(metalake, catalog, schema, metric);
            MetricChange[] changes =
                request.getUpdates().stream()
                    .map(MetricUpdateRequest::metricChange)
                    .toArray(MetricChange[]::new);
            Metric m = datasetDispatcher.alterMetric(ident, changes);
            return Utils.ok(new MetricResponse(DTOConverters.toDTO(m)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.ALTER, metric, schema, e);
    }
  }

  @GET
  @Path("{metric}/versions")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "list-metric-versions." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-metric-versions", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response listMetricVersions(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            int[] versions = datasetDispatcher.listMetricVersions(metricId);
            versions = versions == null ? new int[0] : versions;
            return Utils.ok(new MetricVersionListResponse(versions));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.LIST_VERSIONS, metric, schema, e);
    }
  }

  @GET
  @Path("{metric}/versions/{version}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "get-metric-version." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "get-metric-version", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response getMetricVersion(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric,
      @PathParam("version") int version) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            MetricVersion mv = datasetDispatcher.getMetricVersion(metricId, version);
            return Utils.ok(new MetricVersionResponse(DTOConverters.toDTO(mv)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.GET, versionString(metric, version), schema, e);
    }
  }

  @DELETE
  @Path("{metric}/versions/{version}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "delete-metric-version." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "delete-metric-version", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response deleteMetricVersion(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric,
      @PathParam("version") int version) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            boolean deleted = datasetDispatcher.deleteMetricVersion(metricId, version);
            return Utils.ok(new DropResponse(deleted));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.DELETE, versionString(metric, version), schema, e);
    }
  }

  @PUT
  @Path("{metric}/versions/{version}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "alter-metric-version." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "alter-metric-version", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  @Operation(
      summary = "Update indicator version",
      description =
          "Update information about the specified indicator version（A new version will be automatically created）")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated version"),
        @ApiResponse(responseCode = "404", description = "Indicator version does not exist")
      })
  public Response alterMetricVersion(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric,
      @PathParam("version") int version,
      MetricVersionUpdateRequest request) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            MetricVersion mv =
                datasetDispatcher.alterMetricVersion(
                    metricId,
                    version,
                    request.getMetricName(),
                    request.getMetricCode(),
                    request.getMetricType(),
                    request.getDataType(),
                    request.getComment(),
                    request.getUnit(),
                    request.getUnitName(),
                    request.getParentMetricCodes(),
                    request.getCalculationFormula(),
                    request.getRefTableId(),
                    request.getMeasureColumnIds(),
                    request.getFilterColumnIds());
            return Utils.ok(new MetricVersionResponse(DTOConverters.toDTO(mv)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.ALTER, versionString(metric, version), schema, e);
    }
  }

  @PUT
  @Path("{metric}/switch/versions/{version}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "switch-metric-version." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "switch-metric-version", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response switchMetricVersion(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("metric") @AuthorizationMetadata(type = Entity.EntityType.METRIC) String metric,
      @PathParam("version") int version) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            MetricVersion metricVersion = datasetDispatcher.switchMetricVersion(metricId, version);
            return Utils.ok(new MetricVersionResponse(DTOConverters.toDTO(metricVersion)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.ALTER, versionString(metric, version), schema, e);
    }
  }

  @GET
  @Path("modifiers")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "list-modifiers." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-modifiers", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response listModifiers(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") @DefaultValue("20") int limit) {
    Namespace modifierNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            PagedResult<MetricModifier> result =
                datasetDispatcher.listMetricModifiers(modifierNs, offset, limit);
            MetricModifierDTO[] modifierDTOs =
                result.items().stream().map(DTOConverters::toDTO).toArray(MetricModifierDTO[]::new);
            return Utils.ok(
                new MetricModifierListResponse(modifierDTOs, result.total(), offset, limit));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.LIST, "", schema, e);
    }
  }

  @GET
  @Path("modifiers/{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "get-modifier." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "get-modifier", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response getModifier(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code) {
    NameIdentifier modifierId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.MetricModifier modifier =
                datasetDispatcher.getMetricModifier(modifierId);
            return Utils.ok(new MetricModifierResponse(DTOConverters.toDTO(modifier)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.GET, code, schema, e);
    }
  }

  @POST
  @Path("modifiers")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "create-modifier." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "create-modifier", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response createModifier(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      MetricModifierCreateRequest request) {

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            NameIdentifier modifierId =
                NameIdentifier.of(metalake, catalog, schema, request.getName());
            org.apache.gravitino.dataset.MetricModifier modifier =
                datasetDispatcher.createMetricModifier(
                    modifierId, request.getCode(), request.getComment(), request.getModifierType());

            return Utils.ok(new MetricModifierResponse(DTOConverters.toDTO(modifier)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.CREATE, request.getName(), schema, e);
    }
  }

  @DELETE
  @Path("modifiers/{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "delete-modifier." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "delete-modifier", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response deleteModifier(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code) {
    NameIdentifier modifierId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            boolean deleted = datasetDispatcher.deleteMetricModifier(modifierId);
            return Utils.ok(new DropResponse(deleted));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.DELETE, code, schema, e);
    }
  }

  @PUT
  @Path("modifiers/{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "alter-modifier." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "alter-modifier", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  @Operation(
      summary = "update modifier",
      description = "Update information for a specified modifier")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated modifiers"),
        @ApiResponse(responseCode = "404", description = "Modifier does not exist")
      })
  public Response alterModifier(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code,
      MetricModifierUpdateRequest request) {
    NameIdentifier modifierId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.MetricModifier modifier =
                datasetDispatcher.alterMetricModifier(
                    modifierId, request.getName(), request.getComment());
            return Utils.ok(new MetricModifierResponse(DTOConverters.toDTO(modifier)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(OperationType.ALTER, code, schema, e);
    }
  }

  private String versionString(String metric, int version) {
    return metric + " version(" + version + ")";
  }
}
