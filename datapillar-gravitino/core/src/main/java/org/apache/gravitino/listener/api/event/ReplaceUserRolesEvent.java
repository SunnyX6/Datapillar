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

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.listener.api.info.UserInfo;
import org.apache.gravitino.utils.NameIdentifierUtil;

/** Represents an event triggered after replacing all roles of a user. */
@DeveloperApi
public class ReplaceUserRolesEvent extends UserEvent {
  private final UserInfo replacedUserInfo;
  private final List<String> roles;

  public ReplaceUserRolesEvent(
      String initiator, String metalake, UserInfo replacedUserInfo, List<String> roles) {
    super(initiator, NameIdentifierUtil.ofUser(metalake, replacedUserInfo.name()));

    this.replacedUserInfo = replacedUserInfo;
    this.roles = roles == null ? ImmutableList.of() : ImmutableList.copyOf(roles);
  }

  public UserInfo replacedUserInfo() {
    return replacedUserInfo;
  }

  public List<String> roles() {
    return roles;
  }

  @Override
  public OperationType operationType() {
    return OperationType.REPLACE_USER_ROLES;
  }
}
