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
package org.apache.gravitino.catalog.metric;

import org.apache.gravitino.Auditable;
import org.apache.gravitino.meta.AuditInfo;
import org.apache.gravitino.metric.MetricModifier;

/** Modifier 接口的实现类 */
public class ModifierImpl implements MetricModifier, Auditable {

  private String name;
  private String code;
  private Type type;
  private String comment;
  private AuditInfo auditInfo;

  private ModifierImpl() {}

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
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  /** Builder 类用于构建 ModifierImpl 实例 */
  public static class Builder {
    private final ModifierImpl modifier;

    private Builder() {
      modifier = new ModifierImpl();
    }

    public Builder withName(String name) {
      modifier.name = name;
      return this;
    }

    public Builder withCode(String code) {
      modifier.code = code;
      return this;
    }

    public Builder withType(Type type) {
      modifier.type = type;
      return this;
    }

    public Builder withComment(String comment) {
      modifier.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      modifier.auditInfo = auditInfo;
      return this;
    }

    public ModifierImpl build() {
      return modifier;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
