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
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.dto.AuditDTO;

/** 表示词根的 DTO (Data Transfer Object) */
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class WordRootDTO implements WordRoot {

  @JsonProperty("code")
  private String code;

  @JsonProperty("nameCn")
  private String nameCn;

  @JsonProperty("nameEn")
  private String nameEn;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("audit")
  private AuditDTO audit;

  @Override
  public String code() {
    return code;
  }

  @Override
  public String nameCn() {
    return nameCn;
  }

  @Override
  public String nameEn() {
    return nameEn;
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

  /** Builder for constructing a WordRoot DTO. */
  public static class Builder {
    private String code;
    private String nameCn;
    private String nameEn;
    private String comment;
    private AuditDTO audit;

    public Builder withCode(String code) {
      this.code = code;
      return this;
    }

    public Builder withNameCn(String nameCn) {
      this.nameCn = nameCn;
      return this;
    }

    public Builder withNameEn(String nameEn) {
      this.nameEn = nameEn;
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

    public WordRootDTO build() {
      Preconditions.checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty");
      Preconditions.checkArgument(StringUtils.isNotBlank(nameCn), "nameCn cannot be null or empty");
      Preconditions.checkArgument(StringUtils.isNotBlank(nameEn), "nameEn cannot be null or empty");
      Preconditions.checkArgument(audit != null, "audit cannot be null");

      return new WordRootDTO(code, nameCn, nameEn, comment, audit);
    }
  }
}
