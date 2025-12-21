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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.apache.gravitino.Entity;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.catalog.DatasetDispatcher;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dto.requests.MetricModifierCreateRequest;
import org.apache.gravitino.dto.requests.MetricRegisterRequest;
import org.apache.gravitino.dto.requests.MetricSwitchVersionRequest;
import org.apache.gravitino.dto.requests.MetricUpdateRequest;
import org.apache.gravitino.dto.requests.MetricUpdatesRequest;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.EntityListResponse;
import org.apache.gravitino.dto.responses.MetricModifierResponse;
import org.apache.gravitino.dto.responses.MetricResponse;
import org.apache.gravitino.dto.responses.MetricVersionListResponse;
import org.apache.gravitino.dto.responses.MetricVersionResponse;
import org.apache.gravitino.dto.util.DTOConverters;
import org.apache.gravitino.metrics.MetricNames;
import org.apache.gravitino.server.authorization.MetadataFilterHelper;
import org.apache.gravitino.server.authorization.annotations.AuthorizationExpression;
import org.apache.gravitino.server.authorization.annotations.AuthorizationMetadata;
import org.apache.gravitino.server.authorization.expression.AuthorizationExpressionConstants;
import org.apache.gravitino.server.web.Utils;

@Tag(name = "Metric Management", description = "指标管理相关API")
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
  @Operation(summary = "列出所有指标", description = "列出指定schema下的所有指标")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "成功返回指标列表"),
        @ApiResponse(responseCode = "404", description = "Schema不存在")
      })
  public Response listMetrics(
      @Parameter(description = "Metalake名称", required = true)
          @PathParam("metalake")
          @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @Parameter(description = "Catalog名称", required = true)
          @PathParam("catalog")
          @AuthorizationMetadata(type = Entity.EntityType.CATALOG)
          String catalog,
      @Parameter(description = "Schema名称", required = true)
          @PathParam("schema")
          @AuthorizationMetadata(type = Entity.EntityType.SCHEMA)
          String schema) {
    Namespace metricNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            NameIdentifier[] metricIds = datasetDispatcher.listMetrics(metricNs);
            metricIds = metricIds == null ? new NameIdentifier[0] : metricIds;
            metricIds =
                MetadataFilterHelper.filterByExpression(
                    metalake,
                    AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
                    Entity.EntityType.METRIC,
                    metricIds);
            return Utils.ok(new EntityListResponse(metricIds));
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
      NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, request.getName());

      return Utils.doAs(
          httpRequest,
          () -> {
            Metric m =
                datasetDispatcher.registerMetric(
                    metricId,
                    request.getCode(),
                    request.getType(),
                    request.getComment(),
                    request.getProperties(),
                    request.getUnit(),
                    request.getAggregationLogic(),
                    request.getParentMetricIds(),
                    request.getCalculationFormula());
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
  @Path("{metric}/switch")
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
      MetricSwitchVersionRequest request) {
    NameIdentifier metricId = NameIdentifier.of(metalake, catalog, schema, metric);

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            Metric updatedMetric =
                datasetDispatcher.switchMetricVersion(metricId, request.getVersion());
            return Utils.ok(new MetricResponse(DTOConverters.toDTO(updatedMetric)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleMetricException(
          OperationType.ALTER, versionString(metric, request.getVersion()), schema, e);
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
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema) {
    Namespace modifierNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            NameIdentifier[] modifierIds = datasetDispatcher.listMetricModifiers(modifierNs);
            return Utils.ok(new EntityListResponse(modifierIds));
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
                    modifierId, request.getCode(), request.getType(), request.getComment());
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

  private String versionString(String metric, int version) {
    return metric + " version(" + version + ")";
  }
}
