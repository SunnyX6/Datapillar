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
package org.apache.gravitino.datapillar.cache;

import com.google.common.base.Preconditions;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;

/** Builds tenant-aware cache keys for Datapillar tenant isolation. */
public final class TenantCacheKeyBuilder {
  private static final long DEFAULT_TENANT_ID = 0L;

  private TenantCacheKeyBuilder() {}

  public static long resolveTenantId() {
    TenantContext tenantContext = TenantContextHolder.get();
    if (tenantContext == null) {
      return DEFAULT_TENANT_ID;
    }
    return tenantContext.tenantId();
  }

  public static String buildPrefix(NameIdentifier identifier) {
    return buildPrefix(resolveTenantId(), identifier);
  }

  public static String buildPrefix(long tenantId, NameIdentifier identifier) {
    Preconditions.checkArgument(tenantId >= 0, "tenantId cannot be negative");
    Preconditions.checkArgument(identifier != null, "identifier cannot be null");
    return String.format("t%d:%s", tenantId, identifier);
  }

  public static TenantCatalogCacheKey buildCatalogCacheKey(NameIdentifier identifier) {
    return TenantCatalogCacheKey.of(resolveTenantId(), identifier);
  }
}
