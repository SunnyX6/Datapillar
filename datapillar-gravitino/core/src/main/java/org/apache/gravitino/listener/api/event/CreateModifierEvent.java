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

/** Represents an event that is generated after a modifier is successfully created. */
@DeveloperApi
public class CreateModifierEvent extends ModifierEvent {
  private final ModifierInfo createdModifierInfo;

  /**
   * Constructs an instance of {@link CreateModifierEvent}.
   *
   * @param user The user responsible for triggering the modifier operation.
   * @param identifier The identifier of the Modifier involved in the operation.
   * @param createdModifierInfo The final state of the modifier post-creation.
   */
  public CreateModifierEvent(
      String user, NameIdentifier identifier, ModifierInfo createdModifierInfo) {
    super(user, identifier);
    this.createdModifierInfo = createdModifierInfo;
  }

  /**
   * Retrieves the final state of the modifier after successful creation.
   *
   * @return The modifier information.
   */
  public ModifierInfo createdModifierInfo() {
    return createdModifierInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.CREATE_METRIC_MODIFIER;
  }
}
