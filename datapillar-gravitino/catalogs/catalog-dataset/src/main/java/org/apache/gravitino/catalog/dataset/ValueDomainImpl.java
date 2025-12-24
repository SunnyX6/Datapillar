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
package org.apache.gravitino.catalog.dataset;

import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.meta.AuditInfo;

/** 值域实现类 */
public class ValueDomainImpl implements ValueDomain {

  private String domainCode;
  private String domainName;
  private Type domainType;
  private String itemValue;
  private String itemLabel;
  private String comment;
  private AuditInfo auditInfo;

  private ValueDomainImpl() {}

  @Override
  public String domainCode() {
    return domainCode;
  }

  @Override
  public String domainName() {
    return domainName;
  }

  @Override
  public Type domainType() {
    return domainType;
  }

  @Override
  public String itemValue() {
    return itemValue;
  }

  @Override
  public String itemLabel() {
    return itemLabel;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ValueDomainImpl impl = new ValueDomainImpl();

    public Builder withDomainCode(String domainCode) {
      impl.domainCode = domainCode;
      return this;
    }

    public Builder withDomainName(String domainName) {
      impl.domainName = domainName;
      return this;
    }

    public Builder withDomainType(Type domainType) {
      impl.domainType = domainType;
      return this;
    }

    public Builder withItemValue(String itemValue) {
      impl.itemValue = itemValue;
      return this;
    }

    public Builder withItemLabel(String itemLabel) {
      impl.itemLabel = itemLabel;
      return this;
    }

    public Builder withComment(String comment) {
      impl.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      impl.auditInfo = auditInfo;
      return this;
    }

    public ValueDomainImpl build() {
      return impl;
    }
  }
}
