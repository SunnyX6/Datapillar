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
import java.util.Objects;
import org.apache.gravitino.NameIdentifier;

/** Tenant-aware cache key for catalog cache entries. */
public final class TenantCatalogCacheKey {
  private final long tenantId;
  private final NameIdentifier identifier;

  private TenantCatalogCacheKey(long tenantId, NameIdentifier identifier) {
    Preconditions.checkArgument(tenantId >= 0, "tenantId cannot be negative");
    Preconditions.checkArgument(identifier != null, "identifier cannot be null");
    this.tenantId = tenantId;
    this.identifier = identifier;
  }

  public static TenantCatalogCacheKey of(long tenantId, NameIdentifier identifier) {
    return new TenantCatalogCacheKey(tenantId, identifier);
  }

  public long tenantId() {
    return tenantId;
  }

  public NameIdentifier identifier() {
    return identifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TenantCatalogCacheKey)) {
      return false;
    }
    TenantCatalogCacheKey that = (TenantCatalogCacheKey) o;
    return tenantId == that.tenantId && Objects.equals(identifier, that.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, identifier);
  }

  @Override
  public String toString() {
    return TenantCacheKeyBuilder.buildPrefix(tenantId, identifier);
  }
}
