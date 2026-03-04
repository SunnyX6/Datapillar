/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/
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
 * express Schema {@link Namespace} Indicator object under.The indicator is Gravitino Managed
 * business metadata objects,Used for unified management of business indicator definitions.*
 *
 * <p>{@link Metric} Defines the basic properties of the indicator object.support {@link
 * DatasetCatalog} of Catalog Implementations should implement this interface.
 */
@Evolving
public interface Metric extends Auditable {

  /** Indicator type enumeration */
  enum Type {
    /** Atomic indicators */
    ATOMIC,
    /** Derived indicators */
    DERIVED,
    /** Composite indicator */
    COMPOSITE
  }

  /**
   * @return Indicator name
   */
  String name();

  /**
   * @return Indicator coding
   */
  String code();

  /**
   * @return Indicator type
   */
  Type type();

  /**
   * @return Indicator notes,Returns if not set null
   */
  default String comment() {
    return null;
  }

  /**
   * @return data type,Such as STRING,INTEGER,DECIMAL(10,2),Returns if not set null
   */
  default String dataType() {
    return null;
  }

  /**
   * @return unit code,Such as CNY,PERCENT,COUNT,Returns if not set null
   */
  default String unit() {
    return null;
  }

  /**
   * @return Unit name,Such as RMB,Percentage,number,Returns if not set null
   */
  default String unitName() {
    return null;
  }

  /**
   * @return Properties of the indicator
   */
  default Map<String, String> properties() {
    return Collections.emptyMap();
  }

  /**
   * @return The current version number of the indicator
   */
  int currentVersion();

  /**
   * @return The latest version number of the indicator
   */
  int lastVersion();

  /**
   * @return If the indicator supports label operations,then return {@link SupportsTags}
   * @throws UnsupportedOperationException If the indicator does not support label operations
   */
  default SupportsTags supportsTags() {
    throw new UnsupportedOperationException("Metric does not support tag operations.");
  }

  /**
   * @return If the indicator supports strategy operations,then return {@link SupportsPolicies}
   * @throws UnsupportedOperationException If the indicator does not support strategy operations
   */
  default SupportsPolicies supportsPolicies() {
    throw new UnsupportedOperationException("Metric does not support policy operations.");
  }

  /**
   * @return If the indicator supports role operations,then return {@link SupportsRoles}
   * @throws UnsupportedOperationException If the indicator does not support role operations
   */
  default SupportsRoles supportsRoles() {
    throw new UnsupportedOperationException("Metric does not support role operations.");
  }
}
