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
import org.apache.gravitino.dataset.Unit;

/** UnitInfo exposes unit information for event listener, it's supposed to be read only. */
@DeveloperApi
public class UnitInfo {
  private final String code;
  private final Optional<String> name;
  private final Optional<String> symbol;
  private final Optional<String> comment;
  private final Optional<Audit> audit;

  /**
   * Constructs a {@link UnitInfo} instance based on a given unit.
   *
   * @param unit the unit to expose information for.
   */
  public UnitInfo(Unit unit) {
    this(unit.code(), unit.name(), unit.symbol(), unit.comment(), unit.auditInfo());
  }

  /**
   * Constructs a {@link UnitInfo} instance based on all fields.
   *
   * @param code the code of the unit.
   * @param name the name of the unit.
   * @param symbol the symbol of the unit.
   * @param comment the comment of the unit.
   * @param audit the audit information of the unit.
   */
  public UnitInfo(String code, String name, String symbol, String comment, Audit audit) {
    this.code = code;
    this.name = Optional.ofNullable(name);
    this.symbol = Optional.ofNullable(symbol);
    this.comment = Optional.ofNullable(comment);
    this.audit = Optional.ofNullable(audit);
  }

  /**
   * Returns the code of the unit.
   *
   * @return the code of the unit.
   */
  public String code() {
    return code;
  }

  /**
   * Returns the name of the unit.
   *
   * @return the name of the unit or empty if not set.
   */
  public Optional<String> name() {
    return name;
  }

  /**
   * Returns the symbol of the unit.
   *
   * @return the symbol of the unit or empty if not set.
   */
  public Optional<String> symbol() {
    return symbol;
  }

  /**
   * Returns the comment of the unit.
   *
   * @return the comment of the unit or empty if not set.
   */
  public Optional<String> comment() {
    return comment;
  }

  /**
   * Returns the audit information of the unit.
   *
   * @return the audit information of the unit or empty if not set.
   */
  public Optional<Audit> audit() {
    return audit;
  }
}
