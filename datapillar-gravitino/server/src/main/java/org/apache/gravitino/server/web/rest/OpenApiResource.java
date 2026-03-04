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

import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** REST API endpoint that provides the OpenAPI specification for tools such as Scalar. */
@Path("/openapi")
public class OpenApiResource {

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiResource.class);

  /**
   * Return OpenAPI specification JSON.
   *
   * @param uriInfo URI information
   * @return OpenAPI specification JSON
   */
  @GET
  @Path("/openapi.json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getOpenApiJson(@Context UriInfo uriInfo) {
    try {
      // Create base OpenAPI configuration.
      OpenAPI openAPI = createOpenAPIConfiguration(uriInfo);

      // Create Swagger configuration.
      SwaggerConfiguration config = new SwaggerConfiguration();
      config.setOpenAPI(openAPI);
      config.setPrettyPrint(true);

      // Scan JAX-RS resource classes with Reader.
      Reader reader = new Reader(config);
      Set<Class<?>> classes = getResourceClasses();

      LOG.info("Start scanning REST resource classes, total {} classes", classes.size());
      openAPI = reader.read(classes);

      LOG.info(
          "OpenAPI specification generated successfully, total {} paths",
          openAPI.getPaths() != null ? openAPI.getPaths().size() : 0);

      return Response.ok(io.swagger.v3.core.util.Json.mapper().writeValueAsString(openAPI)).build();

    } catch (Exception e) {
      LOG.error("Failed to generate OpenAPI specification", e);
      return Response.serverError()
          .entity(
              "{\"error\": \"Failed to generate OpenAPI specification: " + e.getMessage() + "\"}")
          .build();
    }
  }

  /**
   * Create OpenAPI configuration.
   *
   * @param uriInfo URI information
   * @return OpenAPI object
   */
  private OpenAPI createOpenAPIConfiguration(UriInfo uriInfo) {
    OpenAPI openAPI = new OpenAPI();

    // Basic metadata.
    Info info =
        new Info()
            .title("Apache Gravitino API")
            .description(
                "Gravitino is a high-performance geographically distributed federated metadata lake. "
                    + "This document provides a complete REST API reference.")
            .version("1.0.0")
            .contact(
                new Contact()
                    .name("Apache Gravitino")
                    .url("https://gravitino.apache.org")
                    .email("dev@gravitino.apache.org"))
            .license(
                new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0"));

    openAPI.info(info);

    // Server configuration.
    String baseUri = uriInfo.getBaseUri().toString().replaceAll("/+$", "");
    Server server = new Server().url(baseUri).description("Gravitino Server");
    openAPI.addServersItem(server);

    return openAPI;
  }

  /**
   * Get resource classes to scan.
   *
   * @return Resource class collection
   */
  private Set<Class<?>> getResourceClasses() {
    Set<Class<?>> classes = new HashSet<>();

    // Add metric-related REST resource classes.
    classes.add(MetricOperations.class);
    classes.add(WordRootOperations.class);
    classes.add(UnitOperations.class);
    classes.add(ValueDomainOperations.class);

    // Additional REST resource classes can be added here.
    // classes.add(CatalogOperations.class);
    // classes.add(SchemaOperations.class);
    // etc.

    return classes;
  }
}
