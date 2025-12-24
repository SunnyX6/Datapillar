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
import org.apache.gravitino.dto.requests.WordRootCreateRequest;
import org.apache.gravitino.dto.requests.WordRootUpdateRequest;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.PagedEntityListResponse;
import org.apache.gravitino.dto.responses.WordRootResponse;
import org.apache.gravitino.dto.util.DTOConverters;
import org.apache.gravitino.metrics.MetricNames;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.server.authorization.annotations.AuthorizationExpression;
import org.apache.gravitino.server.authorization.annotations.AuthorizationMetadata;
import org.apache.gravitino.server.authorization.expression.AuthorizationExpressionConstants;
import org.apache.gravitino.server.web.Utils;

@Path("metalakes/{metalake}/catalogs/{catalog}/schemas/{schema}/wordroots")
public class WordRootOperations {

  private final DatasetDispatcher datasetDispatcher;

  @Context private HttpServletRequest httpRequest;

  @Inject
  public WordRootOperations(DatasetDispatcher datasetDispatcher) {
    this.datasetDispatcher = datasetDispatcher;
  }

  @GET
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "list-wordroots." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-wordroots", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response listWordRoots(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") @DefaultValue("20") int limit) {
    Namespace rootNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            PagedResult<NameIdentifier> result =
                datasetDispatcher.listWordRoots(rootNs, offset, limit);
            NameIdentifier[] rootIds = result.items().toArray(new NameIdentifier[0]);
            return Utils.ok(new PagedEntityListResponse(rootIds, result.total(), offset, limit));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleWordRootException(OperationType.LIST, "", schema, e);
    }
  }

  @GET
  @Path("{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "get-wordroot." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "get-wordroot", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response getWordRoot(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code) {
    NameIdentifier rootId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.WordRoot root = datasetDispatcher.getWordRoot(rootId);
            return Utils.ok(new WordRootResponse(DTOConverters.toDTO(root)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleWordRootException(OperationType.GET, code, schema, e);
    }
  }

  @POST
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "create-wordroot." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "create-wordroot", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response createWordRoot(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      WordRootCreateRequest request) {

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            NameIdentifier rootId = NameIdentifier.of(metalake, catalog, schema, request.getCode());
            org.apache.gravitino.dataset.WordRoot root =
                datasetDispatcher.createWordRoot(
                    rootId,
                    request.getCode(),
                    request.getName(),
                    request.getDataType(),
                    request.getComment());
            return Utils.ok(new WordRootResponse(DTOConverters.toDTO(root)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleWordRootException(
          OperationType.CREATE, request.getCode(), schema, e);
    }
  }

  @DELETE
  @Path("{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "delete-wordroot." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "delete-wordroot", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response deleteWordRoot(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code) {
    NameIdentifier rootId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            boolean deleted = datasetDispatcher.deleteWordRoot(rootId);
            return Utils.ok(new DropResponse(deleted));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleWordRootException(OperationType.DELETE, code, schema, e);
    }
  }

  @PUT
  @Path("{code}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "alter-wordroot." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "alter-wordroot", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response alterWordRoot(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("code") String code,
      WordRootUpdateRequest request) {
    NameIdentifier rootId = NameIdentifier.of(metalake, catalog, schema, code);

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.WordRoot root =
                datasetDispatcher.alterWordRoot(
                    rootId, request.getName(), request.getDataType(), request.getComment());
            return Utils.ok(new WordRootResponse(DTOConverters.toDTO(root)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleWordRootException(OperationType.ALTER, code, schema, e);
    }
  }
}
