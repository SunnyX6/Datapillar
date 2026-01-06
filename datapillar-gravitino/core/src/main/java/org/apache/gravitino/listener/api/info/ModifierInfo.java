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
  private final Optional<String> name;
  private final Optional<String> comment;
  private final Optional<String> modifierType;
  private final Optional<Audit> audit;

  /**
   * Constructs a {@link ModifierInfo} instance based on a given modifier.
   *
   * @param modifier the modifier to expose information for.
   */
  public ModifierInfo(MetricModifier modifier) {
    this(
        modifier.code(),
        modifier.name(),
        modifier.comment(),
        modifier.modifierType(),
        modifier.auditInfo());
  }

  /**
   * Constructs a {@link ModifierInfo} instance based on all fields.
   *
   * @param code the code of the modifier.
   * @param name the name of the modifier.
   * @param comment the comment of the modifier.
   * @param modifierType the type of the modifier.
   * @param audit the audit information of the modifier.
   */
  public ModifierInfo(String code, String name, String comment, String modifierType, Audit audit) {
    this.code = code;
    this.name = Optional.ofNullable(name);
    this.comment = Optional.ofNullable(comment);
    this.modifierType = Optional.ofNullable(modifierType);
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
   * Returns the name of the modifier.
   *
   * @return the name of the modifier or empty if not set.
   */
  public Optional<String> name() {
    return name;
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
   * Returns the type of the modifier.
   *
   * @return the type of the modifier or empty if not set.
   */
  public Optional<String> modifierType() {
    return modifierType;
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
