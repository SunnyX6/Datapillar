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

import org.apache.gravitino.Auditable;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.meta.AuditInfo;

/** WordRoot 接口的实现类 */
public class WordRootImpl implements WordRoot, Auditable {

  private String code;
  private String nameCn;
  private String nameEn;
  private String comment;
  private AuditInfo auditInfo;

  private WordRootImpl() {}

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
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  /** Builder 类用于构建 WordRootImpl 实例 */
  public static class Builder {
    private final WordRootImpl wordRoot;

    private Builder() {
      wordRoot = new WordRootImpl();
    }

    public Builder withCode(String code) {
      wordRoot.code = code;
      return this;
    }

    public Builder withNameCn(String nameCn) {
      wordRoot.nameCn = nameCn;
      return this;
    }

    public Builder withNameEn(String nameEn) {
      wordRoot.nameEn = nameEn;
      return this;
    }

    public Builder withComment(String comment) {
      wordRoot.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      wordRoot.auditInfo = auditInfo;
      return this;
    }

    public WordRootImpl build() {
      return wordRoot;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
