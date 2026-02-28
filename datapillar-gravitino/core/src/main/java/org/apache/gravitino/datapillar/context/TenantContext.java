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
package org.apache.gravitino.datapillar.context;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

/** Immutable tenant context snapshot for one request. */
public class TenantContext {
  private final long tenantId;
  private final String tenantCode;
  private final String tenantName;

  private TenantContext(Builder builder) {
    this.tenantId = builder.tenantId;
    this.tenantCode = builder.tenantCode;
    this.tenantName = builder.tenantName;
  }

  public static Builder builder() {
    return new Builder();
  }

  public long tenantId() {
    return tenantId;
  }

  public String tenantCode() {
    return tenantCode;
  }

  public String tenantName() {
    return tenantName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TenantContext)) {
      return false;
    }
    TenantContext that = (TenantContext) o;
    return tenantId == that.tenantId
        && Objects.equal(tenantCode, that.tenantCode)
        && Objects.equal(tenantName, that.tenantName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(tenantId, tenantCode, tenantName);
  }

  @Override
  public String toString() {
    return "TenantContext{"
        + "tenantId="
        + tenantId
        + ", tenantCode='"
        + tenantCode
        + '\''
        + ", tenantName='"
        + tenantName
        + '\''
        + '}';
  }

  public static class Builder {
    private long tenantId;
    private String tenantCode;
    private String tenantName;

    private Builder() {}

    public Builder withTenantId(long tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder withTenantCode(String tenantCode) {
      this.tenantCode = tenantCode;
      return this;
    }

    public Builder withTenantName(String tenantName) {
      this.tenantName = tenantName;
      return this;
    }

    public TenantContext build() {
      Preconditions.checkArgument(tenantId > 0, "tenantId must be positive");
      Preconditions.checkArgument(StringUtils.isNotBlank(tenantCode), "tenantCode cannot be blank");
      return new TenantContext(this);
    }
  }
}
