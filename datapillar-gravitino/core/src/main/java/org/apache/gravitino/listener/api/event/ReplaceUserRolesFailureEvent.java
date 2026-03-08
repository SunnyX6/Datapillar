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

import java.util.List;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.utils.NameIdentifierUtil;

/** Represents an event triggered when replacing all roles of a user fails. */
@DeveloperApi
public class ReplaceUserRolesFailureEvent extends UserFailureEvent {
  private final String userName;
  private final List<String> roles;

  public ReplaceUserRolesFailureEvent(
      String initiator, String metalake, Exception exception, String userName, List<String> roles) {
    super(initiator, NameIdentifierUtil.ofUser(metalake, userName), exception);
    this.userName = userName;
    this.roles = roles;
  }

  public String userName() {
    return userName;
  }

  public List<String> roles() {
    return roles;
  }

  @Override
  public OperationType operationType() {
    return OperationType.REPLACE_USER_ROLES;
  }
}
