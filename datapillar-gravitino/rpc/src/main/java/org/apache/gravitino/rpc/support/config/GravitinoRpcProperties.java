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
package org.apache.gravitino.rpc.support.config;

import org.apache.commons.lang3.StringUtils;

/** Runtime properties used by Datapillar Gravitino RPC provider. */
public record GravitinoRpcProperties(String serverUri, String metalake, String authUser) {

  private static final String PROP_SERVER_URI = "datapillar.rpc.gravitino.serverUri";
  private static final String PROP_METALAKE = "datapillar.rpc.gravitino.metalake";
  private static final String PROP_AUTH_USER = "datapillar.rpc.gravitino.authUser";

  private static final String DEFAULT_SERVER_URI = "http://127.0.0.1:8090";
  private static final String DEFAULT_METALAKE = "OneMeta";
  private static final String DEFAULT_AUTH_USER = "anonymous";

  public static GravitinoRpcProperties fromSystemProperties() {
    String serverUri = System.getProperty(PROP_SERVER_URI, DEFAULT_SERVER_URI);
    String metalake = System.getProperty(PROP_METALAKE, DEFAULT_METALAKE);
    String authUser = System.getProperty(PROP_AUTH_USER, DEFAULT_AUTH_USER);

    return new GravitinoRpcProperties(
        StringUtils.defaultIfBlank(serverUri, DEFAULT_SERVER_URI),
        StringUtils.defaultIfBlank(metalake, DEFAULT_METALAKE),
        StringUtils.defaultIfBlank(authUser, DEFAULT_AUTH_USER));
  }
}
