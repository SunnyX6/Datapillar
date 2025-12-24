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
import org.apache.gravitino.listener.api.info.UnitInfo;

/** Represents an event that is generated after a unit is successfully created. */
@DeveloperApi
public class CreateUnitEvent extends UnitEvent {
  private final UnitInfo createdUnitInfo;

  /**
   * Constructs an instance of {@link CreateUnitEvent}.
   *
   * @param user The user responsible for triggering the unit operation.
   * @param identifier The identifier of the Unit involved in the operation.
   * @param createdUnitInfo The final state of the unit post-creation.
   */
  public CreateUnitEvent(String user, NameIdentifier identifier, UnitInfo createdUnitInfo) {
    super(user, identifier);
    this.createdUnitInfo = createdUnitInfo;
  }

  /**
   * Retrieves the final state of the unit after successful creation.
   *
   * @return The unit information.
   */
  public UnitInfo createdUnitInfo() {
    return createdUnitInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.CREATE_UNIT;
  }
}
