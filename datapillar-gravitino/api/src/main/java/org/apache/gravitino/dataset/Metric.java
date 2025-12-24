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
package org.apache.gravitino.dataset;

import java.util.Collections;
import java.util.Map;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.annotation.Evolving;
import org.apache.gravitino.authorization.SupportsRoles;
import org.apache.gravitino.policy.SupportsPolicies;
import org.apache.gravitino.tag.SupportsTags;

/**
 * 表示 Schema {@link Namespace} 下的指标对象。 指标是 Gravitino 管理的业务元数据对象，用于统一管理业务指标定义。
 *
 * <p>{@link Metric} 定义了指标对象的基本属性。支持 {@link DatasetCatalog} 的 Catalog 实现应该实现此接口。
 */
@Evolving
public interface Metric extends Auditable {

  /** 指标类型枚举 */
  enum Type {
    /** 原子指标 */
    ATOMIC,
    /** 派生指标 */
    DERIVED,
    /** 复合指标 */
    COMPOSITE
  }

  /**
   * @return 指标名称
   */
  String name();

  /**
   * @return 指标编码
   */
  String code();

  /**
   * @return 指标类型
   */
  Type type();

  /**
   * @return 指标注释，如果未设置则返回 null
   */
  default String comment() {
    return null;
  }

  /**
   * @return 数据类型，如 STRING, INTEGER, DECIMAL(10,2)，如果未设置则返回 null
   */
  default String dataType() {
    return null;
  }

  /**
   * @return 指标的属性
   */
  default Map<String, String> properties() {
    return Collections.emptyMap();
  }

  /**
   * @return 指标的当前版本
   */
  int currentVersion();

  /**
   * @return 指标的最后版本
   */
  int lastVersion();

  /**
   * @return 如果指标支持标签操作，则返回 {@link SupportsTags}
   * @throws UnsupportedOperationException 如果指标不支持标签操作
   */
  default SupportsTags supportsTags() {
    throw new UnsupportedOperationException("Metric does not support tag operations.");
  }

  /**
   * @return 如果指标支持策略操作，则返回 {@link SupportsPolicies}
   * @throws UnsupportedOperationException 如果指标不支持策略操作
   */
  default SupportsPolicies supportsPolicies() {
    throw new UnsupportedOperationException("Metric does not support policy operations.");
  }

  /**
   * @return 如果指标支持角色操作，则返回 {@link SupportsRoles}
   * @throws UnsupportedOperationException 如果指标不支持角色操作
   */
  default SupportsRoles supportsRoles() {
    throw new UnsupportedOperationException("Metric does not support role operations.");
  }
}
