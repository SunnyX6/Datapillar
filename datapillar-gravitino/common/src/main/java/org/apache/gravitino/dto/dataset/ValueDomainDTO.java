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
package org.apache.gravitino.dto.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dto.AuditDTO;

/** 值域 DTO */
@ToString
@EqualsAndHashCode
public class ValueDomainDTO implements ValueDomain {

  @JsonProperty("domainCode")
  private String domainCode;

  @JsonProperty("domainName")
  private String domainName;

  @JsonProperty("domainType")
  private Type domainType;

  @JsonProperty("itemValue")
  private String itemValue;

  @JsonProperty("itemLabel")
  private String itemLabel;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("audit")
  private AuditDTO audit;

  private ValueDomainDTO() {}

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
  public AuditDTO auditInfo() {
    return audit;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ValueDomainDTO dto = new ValueDomainDTO();

    public Builder withDomainCode(String domainCode) {
      dto.domainCode = domainCode;
      return this;
    }

    public Builder withDomainName(String domainName) {
      dto.domainName = domainName;
      return this;
    }

    public Builder withDomainType(Type domainType) {
      dto.domainType = domainType;
      return this;
    }

    public Builder withItemValue(String itemValue) {
      dto.itemValue = itemValue;
      return this;
    }

    public Builder withItemLabel(String itemLabel) {
      dto.itemLabel = itemLabel;
      return this;
    }

    public Builder withComment(String comment) {
      dto.comment = comment;
      return this;
    }

    public Builder withAudit(AuditDTO audit) {
      dto.audit = audit;
      return this;
    }

    public ValueDomainDTO build() {
      return dto;
    }
  }
}
