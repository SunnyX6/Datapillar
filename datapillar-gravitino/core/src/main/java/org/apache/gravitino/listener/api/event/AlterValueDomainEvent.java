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
import org.apache.gravitino.listener.api.info.ValueDomainInfo;

/** Represents an event that is generated after a value domain is successfully altered. */
@DeveloperApi
public class AlterValueDomainEvent extends ValueDomainEvent {
  private final ValueDomainInfo updatedValueDomainInfo;

  /**
   * Constructs an instance of {@link AlterValueDomainEvent}.
   *
   * @param user The user responsible for triggering the value domain operation.
   * @param identifier The identifier of the ValueDomain involved in the operation.
   * @param updatedValueDomainInfo The final state of the value domain post-alteration.
   */
  public AlterValueDomainEvent(
      String user, NameIdentifier identifier, ValueDomainInfo updatedValueDomainInfo) {
    super(user, identifier);
    this.updatedValueDomainInfo = updatedValueDomainInfo;
  }

  /**
   * Retrieves the final state of the value domain after successful alteration.
   *
   * @return The value domain information.
   */
  public ValueDomainInfo updatedValueDomainInfo() {
    return updatedValueDomainInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.ALTER_VALUE_DOMAIN;
  }
}
