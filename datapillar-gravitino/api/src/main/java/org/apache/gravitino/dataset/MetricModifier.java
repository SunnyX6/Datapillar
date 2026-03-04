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

import javax.annotation.Nullable;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * MetricModifier Interface represents indicator modifiers
 *
 * <p>Modifiers are used to classify and label metrics，Modifier types pass through associated value
 * fields（ValueDomain）to define
 */
@Evolving
public interface MetricModifier extends Auditable {

  /**
   * Get modifier name
   *
   * @return Modifier name
   */
  String name();

  /**
   * Get modifier encoding
   *
   * @return modifier encoding
   */
  String code();

  /**
   * Get modifier annotation
   *
   * @return Modifier annotation
   */
  String comment();

  /**
   * Get modifier type，from value range
   *
   * @return modifier type，Can be null
   */
  @Nullable
  String modifierType();
}
