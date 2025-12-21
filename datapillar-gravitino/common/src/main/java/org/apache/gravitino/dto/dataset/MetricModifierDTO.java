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
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.Audit;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dto.AuditDTO;

/** 表示指标修饰符的 DTO (Data Transfer Object) */
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class MetricModifierDTO implements MetricModifier {

  @JsonProperty("name")
  private String name;

  @JsonProperty("code")
  private String code;

  @JsonProperty("type")
  private Type type;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("audit")
  private AuditDTO audit;

  @Override
  public String name() {
    return name;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Type type() {
    return type;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public Audit auditInfo() {
    return audit;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing a Modifier DTO. */
  public static class Builder {
    private String name;
    private String code;
    private Type type;
    private String comment;
    private AuditDTO audit;

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withCode(String code) {
      this.code = code;
      return this;
    }

    public Builder withType(Type type) {
      this.type = type;
      return this;
    }

    public Builder withComment(String comment) {
      this.comment = comment;
      return this;
    }

    public Builder withAudit(AuditDTO audit) {
      this.audit = audit;
      return this;
    }

    public MetricModifierDTO build() {
      Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be null or empty");
      Preconditions.checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty");
      Preconditions.checkArgument(type != null, "type cannot be null");
      Preconditions.checkArgument(audit != null, "audit cannot be null");

      return new MetricModifierDTO(name, code, type, comment, audit);
    }
  }
}
