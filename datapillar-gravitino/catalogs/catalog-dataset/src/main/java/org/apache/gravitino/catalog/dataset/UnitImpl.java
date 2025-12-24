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
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.meta.AuditInfo;

/** Unit 接口的实现类 */
public class UnitImpl implements Unit, Auditable {

  private String code;
  private String name;
  private String symbol;
  private String comment;
  private AuditInfo auditInfo;

  private UnitImpl() {}

  @Override
  public String code() {
    return code;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String symbol() {
    return symbol;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  /** Builder 类用于构建 UnitImpl 实例 */
  public static class Builder {
    private final UnitImpl unit;

    private Builder() {
      unit = new UnitImpl();
    }

    public Builder withCode(String code) {
      unit.code = code;
      return this;
    }

    public Builder withName(String name) {
      unit.name = name;
      return this;
    }

    public Builder withSymbol(String symbol) {
      unit.symbol = symbol;
      return this;
    }

    public Builder withComment(String comment) {
      unit.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      unit.auditInfo = auditInfo;
      return this;
    }

    public UnitImpl build() {
      return unit;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
