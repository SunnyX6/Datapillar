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

package org.apache.gravitino.listener.api.event;

import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.listener.api.info.ModifierInfo;

/** Represents an event that is generated after a modifier is successfully altered. */
@DeveloperApi
public class AlterModifierEvent extends ModifierEvent {
  private final ModifierInfo updatedModifierInfo;

  /**
   * Constructs an instance of {@link AlterModifierEvent}.
   *
   * @param user The user responsible for triggering the modifier operation.
   * @param identifier The identifier of the Modifier involved in the operation.
   * @param updatedModifierInfo The final state of the modifier post-alteration.
   */
  public AlterModifierEvent(
      String user, NameIdentifier identifier, ModifierInfo updatedModifierInfo) {
    super(user, identifier);
    this.updatedModifierInfo = updatedModifierInfo;
  }

  /**
   * Retrieves the final state of the modifier after successful alteration.
   *
   * @return The modifier information.
   */
  public ModifierInfo updatedModifierInfo() {
    return updatedModifierInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.ALTER_METRIC_MODIFIER;
  }
}
