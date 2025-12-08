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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/** REST API端点，用于提供Scalar API文档UI界面 */
@Path("/docs")
public class ScalarUiResource {

  /**
   * 提供Scalar UI界面
   *
   * @param uriInfo URI信息
   * @return HTML响应
   */
  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response getScalarUi(@Context UriInfo uriInfo) {
    String baseUri = uriInfo.getBaseUri().toString().replaceAll("/+$", "");
    String openApiUrl = baseUri + "/openapi/openapi.json";

    String html =
        "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            + "  <title>Gravitino API Documentation</title>\n"
            + "  <style>\n"
            + "    body { margin: 0; padding: 0; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <script id=\"api-reference\" data-url=\""
            + openApiUrl
            + "\"></script>\n"
            + "  <script>\n"
            + "    var configuration = {\n"
            + "      theme: 'purple',\n"
            + "      layout: 'modern',\n"
            + "      darkMode: false,\n"
            + "      showSidebar: true,\n"
            + "    }\n"
            + "    var apiReference = document.getElementById('api-reference')\n"
            + "    apiReference.dataset.configuration = JSON.stringify(configuration)\n"
            + "  </script>\n"
            + "  <!-- 使用Scalar最新版本 (1.25.48) -->\n"
            + "  <script src=\"https://cdn.jsdelivr.net/npm/@scalar/api-reference@1.25.48\"></script>\n"
            + "</body>\n"
            + "</html>";

    return Response.ok(html).build();
  }
}
