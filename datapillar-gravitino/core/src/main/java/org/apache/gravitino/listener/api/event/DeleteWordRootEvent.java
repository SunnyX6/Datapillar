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

/** Represents an event that is generated after a word root is successfully deleted. */
@DeveloperApi
public class DeleteWordRootEvent extends WordRootEvent {
  private final boolean isExists;

  /**
   * Constructs an instance of {@link DeleteWordRootEvent}.
   *
   * @param user The user responsible for triggering the word root operation.
   * @param identifier The identifier of the WordRoot involved in the operation.
   * @param isExists A boolean flag indicating whether the word root existed before deletion.
   */
  public DeleteWordRootEvent(String user, NameIdentifier identifier, boolean isExists) {
    super(user, identifier);
    this.isExists = isExists;
  }

  /**
   * Retrieves the existence status of the word root before deletion.
   *
   * @return true if the word root existed, false otherwise.
   */
  public boolean isExists() {
    return isExists;
  }

  @Override
  public OperationType operationType() {
    return OperationType.DELETE_WORDROOT;
  }
}
