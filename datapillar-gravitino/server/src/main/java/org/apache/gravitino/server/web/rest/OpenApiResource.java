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

/** REST API端点，用于提供OpenAPI规范（供Scalar等文档工具使用） */
@Path("/openapi")
public class OpenApiResource {

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiResource.class);

  /**
   * 获取OpenAPI规范JSON
   *
   * @param uriInfo URI信息
   * @return OpenAPI规范JSON
   */
  @GET
  @Path("/openapi.json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getOpenApiJson(@Context UriInfo uriInfo) {
    try {
      // 创建基础OpenAPI配置
      OpenAPI openAPI = createOpenAPIConfiguration(uriInfo);

      // 创建Swagger配置
      SwaggerConfiguration config = new SwaggerConfiguration();
      config.setOpenAPI(openAPI);
      config.setPrettyPrint(true);

      // 使用Reader扫描JAX-RS资源类
      Reader reader = new Reader(config);
      Set<Class<?>> classes = getResourceClasses();

      LOG.info("开始扫描REST资源类，共{}个类", classes.size());
      openAPI = reader.read(classes);

      LOG.info("OpenAPI规范生成成功，共{}个路径", openAPI.getPaths() != null ? openAPI.getPaths().size() : 0);

      return Response.ok(io.swagger.v3.core.util.Json.mapper().writeValueAsString(openAPI)).build();

    } catch (Exception e) {
      LOG.error("生成OpenAPI规范失败", e);
      return Response.serverError()
          .entity("{\"error\": \"生成OpenAPI规范失败: " + e.getMessage() + "\"}")
          .build();
    }
  }

  /**
   * 创建OpenAPI配置
   *
   * @param uriInfo URI信息
   * @return OpenAPI对象
   */
  private OpenAPI createOpenAPIConfiguration(UriInfo uriInfo) {
    OpenAPI openAPI = new OpenAPI();

    // 基本信息
    Info info =
        new Info()
            .title("Apache Gravitino API")
            .description("Gravitino 是一个高性能、地理分布式的联邦元数据湖。本文档提供了完整的REST API参考。")
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

    // 服务器配置
    String baseUri = uriInfo.getBaseUri().toString().replaceAll("/+$", "");
    Server server = new Server().url(baseUri).description("Gravitino Server");
    openAPI.addServersItem(server);

    return openAPI;
  }

  /**
   * 获取需要扫描的资源类
   *
   * @return 资源类集合
   */
  private Set<Class<?>> getResourceClasses() {
    Set<Class<?>> classes = new HashSet<>();

    // 添加Metric相关的REST资源类
    classes.add(MetricOperations.class);
    classes.add(WordRootOperations.class);
    classes.add(UnitOperations.class);
    classes.add(ValueDomainOperations.class);

    // 可以继续添加其他REST资源类
    // classes.add(CatalogOperations.class);
    // classes.add(SchemaOperations.class);
    // etc.

    return classes;
  }
}
