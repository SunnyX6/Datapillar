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
package org.apache.gravitino.storage.relational.mapper.provider;

import org.apache.gravitino.multitenancy.context.TenantContext;
import org.apache.gravitino.multitenancy.context.TenantContextHolder;

/** Tenant SQL helper utilities for explicit tenant predicates in provider SQL. */
public final class TenantSqlSupport {

  private static final String TENANT_COLUMN = "tenant_id";

  private TenantSqlSupport() {}

  public static long requireTenantId() {
    TenantContext tenantContext = TenantContextHolder.get();
    if (tenantContext == null || tenantContext.tenantId() < 0) {
      throw new IllegalStateException("Missing tenant context for relational SQL provider");
    }
    return tenantContext.tenantId();
  }

  public static String tenantColumn() {
    return TENANT_COLUMN;
  }

  public static String tenantPredicate(String qualifier, long tenantId) {
    if (qualifier == null || qualifier.isBlank()) {
      return TENANT_COLUMN + " = " + tenantId;
    }
    return qualifier + "." + TENANT_COLUMN + " = " + tenantId;
  }
}
