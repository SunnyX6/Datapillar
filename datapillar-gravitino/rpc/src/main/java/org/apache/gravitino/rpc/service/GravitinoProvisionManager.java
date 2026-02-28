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
package org.apache.gravitino.rpc.service;

import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegeCommand;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesRequest;
import com.sunny.datapillar.common.rpc.security.v1.GravitinoObjectType;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Privileges;
import org.apache.gravitino.exceptions.UserAlreadyExistsException;
import org.apache.gravitino.rpc.adapter.gravitino.GravitinoClientAdapter;
import org.apache.gravitino.rpc.support.config.GravitinoRpcProperties;

/** Core business service for Datapillar Gravitino RPC capabilities. */
public class GravitinoProvisionManager {

  private static final String USER_ROLE_PREFIX = "dr_t%d_u%d";

  private final GravitinoClientAdapter clientAdapter;

  public GravitinoProvisionManager() {
    this(new GravitinoClientAdapter(GravitinoRpcProperties.fromSystemProperties()));
  }

  GravitinoProvisionManager(GravitinoClientAdapter clientAdapter) {
    this.clientAdapter = clientAdapter;
  }

  public ProvisionResult provisionUser(ProvisionUserRequest request) throws IOException {
    validateProvisionRequest(request);

    String metalake = resolveMetalake(request.getMetalake());
    String principal = principal(request.getTenantId(), request.getUserId());
    String roleName =
        resolveRoleName(request.getRoleNamesList(), request.getTenantId(), request.getUserId());

    if (clientAdapter.getUserIfExists(metalake, principal) == null) {
      try {
        clientAdapter.addUser(metalake, principal);
      } catch (UserAlreadyExistsException ignored) {
        // idempotent retry: fallback to query
      }
      if (clientAdapter.getUserIfExists(metalake, principal) == null) {
        throw new IllegalStateException(
            "Provision user failed and cannot be queried back: " + principal);
      }
    }

    if (clientAdapter.getRoleIfExists(metalake, roleName) == null) {
      try {
        clientAdapter.createRole(metalake, roleName);
      } catch (Exception roleCreateException) {
        if (clientAdapter.getRoleIfExists(metalake, roleName) == null) {
          throw roleCreateException;
        }
      }
    }

    clientAdapter.grantRoleToUser(metalake, roleName, principal);
    return new ProvisionResult(principal, roleName, request.getTenantId(), request.getUserId());
  }

  public GrantResult grantDataPrivileges(GrantDataPrivilegesRequest request) throws IOException {
    validateGrantRequest(request);

    String metalake = resolveMetalake(request.getMetalake());
    List<GrantOperation> operations = expandGrantOperations(request.getCommandsList());

    for (GrantOperation operation : operations) {
      clientAdapter.grantPrivilegesToRole(
          metalake, operation.roleName(), operation.metadataObject(), operation.privileges());
    }

    return new GrantResult(request.getCommandsCount(), operations.size());
  }

  private List<GrantOperation> expandGrantOperations(List<GrantDataPrivilegeCommand> commands) {
    List<GrantOperation> operations = new ArrayList<>();
    for (GrantDataPrivilegeCommand command : commands) {
      String roleName = requireText(command.getRoleName(), "role_name");
      String objectName = requireText(command.getObjectName(), "object_name");
      GravitinoObjectType objectType = command.getObjectType();
      Set<Privilege> privileges = parsePrivileges(command.getPrivilegeNamesList(), objectType);

      if (objectType == GravitinoObjectType.GRAVITINO_OBJECT_TYPE_TABLE) {
        MetadataObject tableObject = MetadataObjects.parse(objectName, MetadataObject.Type.TABLE);
        operations.add(new GrantOperation(roleName, tableObject, privileges));
      } else if (objectType == GravitinoObjectType.GRAVITINO_OBJECT_TYPE_COLUMN) {
        LinkedHashSet<String> columnNames =
            command.getColumnNamesList().stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (columnNames.isEmpty()) {
          throw new IllegalArgumentException(
              "column_names cannot be empty when object_type=COLUMN");
        }
        for (String columnName : columnNames) {
          MetadataObject columnObject =
              MetadataObjects.parse(objectName + "." + columnName, MetadataObject.Type.COLUMN);
          operations.add(new GrantOperation(roleName, columnObject, privileges));
        }
      } else {
        throw new IllegalArgumentException("Unsupported object_type: " + objectType);
      }
    }
    return operations;
  }

  private Set<Privilege> parsePrivileges(
      List<String> privilegeNames, GravitinoObjectType objectType) {
    if (privilegeNames == null || privilegeNames.isEmpty()) {
      throw new IllegalArgumentException("privilege_names cannot be empty");
    }

    LinkedHashSet<Privilege> privileges = new LinkedHashSet<>();
    for (String rawName : privilegeNames) {
      String normalized = requireText(rawName, "privilege_name").toUpperCase(Locale.ROOT);
      Privilege.Name privilegeName;
      try {
        privilegeName = Privilege.Name.valueOf(normalized);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid privilege name: " + normalized, e);
      }

      if (objectType == GravitinoObjectType.GRAVITINO_OBJECT_TYPE_COLUMN
          && privilegeName != Privilege.Name.SELECT_COLUMN
          && privilegeName != Privilege.Name.MODIFY_COLUMN) {
        throw new IllegalArgumentException(
            "object_type=COLUMN only supports SELECT_COLUMN and MODIFY_COLUMN");
      }

      if (objectType == GravitinoObjectType.GRAVITINO_OBJECT_TYPE_TABLE
          && (privilegeName == Privilege.Name.SELECT_COLUMN
              || privilegeName == Privilege.Name.MODIFY_COLUMN)) {
        throw new IllegalArgumentException(
            "object_type=TABLE does not support SELECT_COLUMN or MODIFY_COLUMN");
      }

      privileges.add(Privileges.allow(privilegeName));
    }

    return privileges;
  }

  private void validateProvisionRequest(ProvisionUserRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request cannot be null");
    }
    if (request.getTenantId() <= 0) {
      throw new IllegalArgumentException("tenant_id must be positive");
    }
    if (request.getUserId() <= 0) {
      throw new IllegalArgumentException("user_id must be positive");
    }
  }

  private void validateGrantRequest(GrantDataPrivilegesRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request cannot be null");
    }
    if (request.getCommandsCount() == 0) {
      throw new IllegalArgumentException("commands cannot be empty");
    }
    if (request.getTenantId() <= 0) {
      throw new IllegalArgumentException("tenant_id must be positive");
    }
    for (GrantDataPrivilegeCommand command : request.getCommandsList()) {
      if (command.getObjectType() == GravitinoObjectType.GRAVITINO_OBJECT_TYPE_UNSPECIFIED) {
        throw new IllegalArgumentException("object_type cannot be unspecified");
      }
    }
  }

  private String resolveRoleName(List<String> roleNames, long tenantId, long userId) {
    if (roleNames == null || roleNames.isEmpty()) {
      return String.format(USER_ROLE_PREFIX, tenantId, userId);
    }

    for (String roleName : roleNames) {
      if (StringUtils.isNotBlank(roleName)) {
        return roleName.trim();
      }
    }
    return String.format(USER_ROLE_PREFIX, tenantId, userId);
  }

  private String resolveMetalake(String requestMetalake) {
    if (StringUtils.isNotBlank(requestMetalake)) {
      return requestMetalake.trim();
    }
    return clientAdapter.defaultMetalake();
  }

  private String principal(long tenantId, long userId) {
    return String.format("uid:%d:%d", tenantId, userId);
  }

  private String requireText(String value, String fieldName) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException(fieldName + " cannot be blank");
    }
    return value.trim();
  }

  private static final class GrantOperation {
    private final String roleName;
    private final MetadataObject metadataObject;
    private final Set<Privilege> privileges;

    private GrantOperation(
        String roleName, MetadataObject metadataObject, Set<Privilege> privileges) {
      this.roleName = roleName;
      this.metadataObject = metadataObject;
      this.privileges = privileges;
    }

    private String roleName() {
      return roleName;
    }

    private MetadataObject metadataObject() {
      return metadataObject;
    }

    private Set<Privilege> privileges() {
      return privileges;
    }
  }

  public record ProvisionResult(String principal, String roleName, long tenantId, long userId) {}

  public record GrantResult(int commandCount, int expandedCommandCount) {}
}
