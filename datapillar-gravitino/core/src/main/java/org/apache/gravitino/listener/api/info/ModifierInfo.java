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

package org.apache.gravitino.listener.api.info;

import java.util.Optional;
import org.apache.gravitino.Audit;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.dataset.MetricModifier;

/** ModifierInfo exposes modifier information for event listener, it's supposed to be read only. */
@DeveloperApi
public class ModifierInfo {
  private final String code;
  private final MetricModifier.Type type;
  private final Optional<String> comment;
  private final Optional<Audit> audit;

  /**
   * Constructs a {@link ModifierInfo} instance based on a given modifier.
   *
   * @param modifier the modifier to expose information for.
   */
  public ModifierInfo(MetricModifier modifier) {
    this(modifier.code(), modifier.type(), modifier.comment(), modifier.auditInfo());
  }

  /**
   * Constructs a {@link ModifierInfo} instance based on all fields.
   *
   * @param code the code of the modifier.
   * @param type the type of the modifier.
   * @param comment the comment of the modifier.
   * @param audit the audit information of the modifier.
   */
  public ModifierInfo(String code, MetricModifier.Type type, String comment, Audit audit) {
    this.code = code;
    this.type = type;
    this.comment = Optional.ofNullable(comment);
    this.audit = Optional.ofNullable(audit);
  }

  /**
   * Returns the code of the modifier.
   *
   * @return the code of the modifier.
   */
  public String code() {
    return code;
  }

  /**
   * Returns the type of the modifier.
   *
   * @return the type of the modifier.
   */
  public MetricModifier.Type modifierType() {
    return type;
  }

  /**
   * Returns the comment of the modifier.
   *
   * @return the comment of the modifier or empty if not set.
   */
  public Optional<String> comment() {
    return comment;
  }

  /**
   * Returns the audit information of the modifier.
   *
   * @return the audit information of the modifier or empty if not set.
   */
  public Optional<Audit> audit() {
    return audit;
  }
}
