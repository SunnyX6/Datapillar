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
import org.apache.gravitino.dto.requests.ValueDomainCreateRequest;
import org.apache.gravitino.dto.requests.ValueDomainUpdateRequest;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.PagedEntityListResponse;
import org.apache.gravitino.dto.responses.ValueDomainResponse;
import org.apache.gravitino.dto.util.DTOConverters;
import org.apache.gravitino.metrics.MetricNames;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.server.authorization.annotations.AuthorizationExpression;
import org.apache.gravitino.server.authorization.annotations.AuthorizationMetadata;
import org.apache.gravitino.server.authorization.expression.AuthorizationExpressionConstants;
import org.apache.gravitino.server.web.Utils;

@Path("metalakes/{metalake}/catalogs/{catalog}/schemas/{schema}/valuedomains")
public class ValueDomainOperations {

  private final DatasetDispatcher datasetDispatcher;

  @Context private HttpServletRequest httpRequest;

  @Inject
  public ValueDomainOperations(DatasetDispatcher datasetDispatcher) {
    this.datasetDispatcher = datasetDispatcher;
  }

  @GET
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "list-valuedomains." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-valuedomains", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response listValueDomains(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") @DefaultValue("20") int limit) {
    Namespace valueDomainNs = Namespace.of(metalake, catalog, schema);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            PagedResult<NameIdentifier> result =
                datasetDispatcher.listValueDomains(valueDomainNs, offset, limit);
            NameIdentifier[] valueDomainIds = result.items().toArray(new NameIdentifier[0]);
            return Utils.ok(
                new PagedEntityListResponse(valueDomainIds, result.total(), offset, limit));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleValueDomainException(OperationType.LIST, "", schema, e);
    }
  }

  @GET
  @Path("{domainCodeItemValue}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "get-valuedomain." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "get-valuedomain", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response getValueDomain(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("domainCodeItemValue") String domainCodeItemValue) {
    NameIdentifier valueDomainId =
        NameIdentifier.of(metalake, catalog, schema, domainCodeItemValue);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.ValueDomain valueDomain =
                datasetDispatcher.getValueDomain(valueDomainId);
            return Utils.ok(new ValueDomainResponse(DTOConverters.toDTO(valueDomain)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleValueDomainException(
          OperationType.GET, domainCodeItemValue, schema, e);
    }
  }

  @POST
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "create-valuedomain." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "create-valuedomain", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response createValueDomain(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      ValueDomainCreateRequest request) {

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            String identName = request.getDomainCode() + ":" + request.getItemValue();
            NameIdentifier valueDomainId = NameIdentifier.of(metalake, catalog, schema, identName);
            org.apache.gravitino.dataset.ValueDomain valueDomain =
                datasetDispatcher.createValueDomain(
                    valueDomainId,
                    request.getDomainCode(),
                    request.getDomainName(),
                    request.getDomainType(),
                    request.getItemValue(),
                    request.getItemLabel(),
                    request.getComment());
            return Utils.ok(new ValueDomainResponse(DTOConverters.toDTO(valueDomain)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleValueDomainException(
          OperationType.CREATE, request.getDomainCode(), schema, e);
    }
  }

  @DELETE
  @Path("{domainCodeItemValue}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "delete-valuedomain." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "delete-valuedomain", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response deleteValueDomain(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("domainCodeItemValue") String domainCodeItemValue) {
    NameIdentifier valueDomainId =
        NameIdentifier.of(metalake, catalog, schema, domainCodeItemValue);

    try {
      return Utils.doAs(
          httpRequest,
          () -> {
            boolean deleted = datasetDispatcher.deleteValueDomain(valueDomainId);
            return Utils.ok(new DropResponse(deleted));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleValueDomainException(
          OperationType.DELETE, domainCodeItemValue, schema, e);
    }
  }

  @PUT
  @Path("{domainCodeItemValue}")
  @Produces("application/vnd.gravitino.v1+json")
  @Timed(name = "alter-valuedomain." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "alter-valuedomain", absolute = true)
  @AuthorizationExpression(
      expression = AuthorizationExpressionConstants.loadSchemaAuthorizationExpression,
      accessMetadataType = MetadataObject.Type.SCHEMA)
  public Response alterValueDomain(
      @PathParam("metalake") @AuthorizationMetadata(type = Entity.EntityType.METALAKE)
          String metalake,
      @PathParam("catalog") @AuthorizationMetadata(type = Entity.EntityType.CATALOG) String catalog,
      @PathParam("schema") @AuthorizationMetadata(type = Entity.EntityType.SCHEMA) String schema,
      @PathParam("domainCodeItemValue") String domainCodeItemValue,
      ValueDomainUpdateRequest request) {
    NameIdentifier valueDomainId =
        NameIdentifier.of(metalake, catalog, schema, domainCodeItemValue);

    try {
      request.validate();

      return Utils.doAs(
          httpRequest,
          () -> {
            org.apache.gravitino.dataset.ValueDomain valueDomain =
                datasetDispatcher.alterValueDomain(
                    valueDomainId,
                    request.getDomainName(),
                    request.getItemLabel(),
                    request.getComment());
            return Utils.ok(new ValueDomainResponse(DTOConverters.toDTO(valueDomain)));
          });

    } catch (Exception e) {
      return ExceptionHandlers.handleValueDomainException(
          OperationType.ALTER, domainCodeItemValue, schema, e);
    }
  }
}
