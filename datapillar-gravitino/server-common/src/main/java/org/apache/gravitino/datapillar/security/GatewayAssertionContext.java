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
package org.apache.gravitino.datapillar.security;

import org.apache.gravitino.datapillar.context.TenantContext;

/** Context extracted from a validated gateway assertion token. */
public class GatewayAssertionContext {
  private final String userId;
  private final String username;
  private final TenantContext tenantContext;

  public GatewayAssertionContext(String userId, String username, TenantContext tenantContext) {
    this.userId = userId;
    this.username = username;
    this.tenantContext = tenantContext;
  }

  public String userId() {
    return userId;
  }

  public String username() {
    return username;
  }

  public TenantContext tenantContext() {
    return tenantContext;
  }
}
