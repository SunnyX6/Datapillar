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
package org.apache.gravitino.rpc.adapter.gravitino;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.User;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.exceptions.NoSuchRoleException;
import org.apache.gravitino.exceptions.NoSuchUserException;
import org.apache.gravitino.exceptions.RoleAlreadyExistsException;
import org.apache.gravitino.exceptions.UserAlreadyExistsException;
import org.apache.gravitino.rpc.support.config.GravitinoRpcProperties;

/** Adapter that encapsulates Gravitino Java Client operations for RPC services. */
public class GravitinoClientAdapter {

  private final GravitinoRpcProperties properties;

  public GravitinoClientAdapter(GravitinoRpcProperties properties) {
    this.properties = properties;
  }

  public User getUserIfExists(String metalake, String principal) throws IOException {
    try (GravitinoClient client = createClient(metalake)) {
      return client.getUser(principal);
    } catch (NoSuchUserException e) {
      return null;
    }
  }

  public User addUser(String metalake, String principal)
      throws IOException, UserAlreadyExistsException {
    try (GravitinoClient client = createClient(metalake)) {
      return client.addUser(principal);
    }
  }

  public Role getRoleIfExists(String metalake, String roleName) throws IOException {
    try (GravitinoClient client = createClient(metalake)) {
      return client.getRole(roleName);
    } catch (NoSuchRoleException e) {
      return null;
    }
  }

  public Role createRole(String metalake, String roleName)
      throws IOException, RoleAlreadyExistsException {
    try (GravitinoClient client = createClient(metalake)) {
      return client.createRole(roleName, Map.of(), List.of());
    }
  }

  public void grantRoleToUser(String metalake, String roleName, String principal)
      throws IOException {
    try (GravitinoClient client = createClient(metalake)) {
      client.grantRolesToUser(List.of(roleName), principal);
    }
  }

  public void grantPrivilegesToRole(
      String metalake, String roleName, MetadataObject object, Set<Privilege> privileges)
      throws IOException {
    try (GravitinoClient client = createClient(metalake)) {
      client.grantPrivilegesToRole(roleName, object, privileges);
    }
  }

  public String defaultMetalake() {
    return properties.metalake();
  }

  private GravitinoClient createClient(String metalake) {
    String resolvedMetalake =
        metalake == null || metalake.isBlank() ? properties.metalake() : metalake;
    return GravitinoClient.builder(properties.serverUri())
        .withMetalake(resolvedMetalake)
        .withSimpleAuth(properties.authUser())
        .withVersionCheckDisabled()
        .build();
  }
}
