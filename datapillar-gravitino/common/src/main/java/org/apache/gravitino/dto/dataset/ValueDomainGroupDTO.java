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
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dto.AuditDTO;

/** 聚合后的值域 DTO，按 domainCode 分组 */
@ToString
@EqualsAndHashCode
public class ValueDomainGroupDTO {

  @JsonProperty("domainCode")
  private String domainCode;

  @JsonProperty("domainName")
  private String domainName;

  @JsonProperty("domainType")
  private ValueDomain.Type domainType;

  @JsonProperty("domainLevel")
  private ValueDomain.Level domainLevel;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("items")
  private List<ValueDomainItemDTO> items;

  @JsonProperty("audit")
  private AuditDTO audit;

  private ValueDomainGroupDTO() {}

  public String domainCode() {
    return domainCode;
  }

  public String domainName() {
    return domainName;
  }

  public ValueDomain.Type domainType() {
    return domainType;
  }

  public ValueDomain.Level domainLevel() {
    return domainLevel;
  }

  public String comment() {
    return comment;
  }

  public List<ValueDomainItemDTO> items() {
    return items;
  }

  public AuditDTO auditInfo() {
    return audit;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ValueDomainGroupDTO dto = new ValueDomainGroupDTO();

    public Builder withDomainCode(String domainCode) {
      dto.domainCode = domainCode;
      return this;
    }

    public Builder withDomainName(String domainName) {
      dto.domainName = domainName;
      return this;
    }

    public Builder withDomainType(ValueDomain.Type domainType) {
      dto.domainType = domainType;
      return this;
    }

    public Builder withDomainLevel(ValueDomain.Level domainLevel) {
      dto.domainLevel = domainLevel;
      return this;
    }

    public Builder withComment(String comment) {
      dto.comment = comment;
      return this;
    }

    public Builder withItems(List<ValueDomainItemDTO> items) {
      dto.items = items;
      return this;
    }

    public Builder withAudit(AuditDTO audit) {
      dto.audit = audit;
      return this;
    }

    public ValueDomainGroupDTO build() {
      return dto;
    }
  }
}
