package com.sunny.datapillar.studio.integration.gravitino;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoGroupResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoGroupSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPrivilegeCommandRequest;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRoleResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRoleSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUserResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.authorization.Group;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Privileges;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.authorization.User;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.exceptions.GroupAlreadyExistsException;
import org.apache.gravitino.exceptions.NotFoundException;
import org.apache.gravitino.exceptions.RoleAlreadyExistsException;
import org.apache.gravitino.exceptions.UserAlreadyExistsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Gravitino admin operations for RBAC resources. */
@Component
public class GravitinoAdminOpsClient {

  private static final String USER_OVERRIDE_ROLE_PREFIX = "user_override_";

  private final GravitinoClientFactory clientFactory;
  private final GravitinoExceptionMapper exceptionMapper;
  private final GravitinoDomainRoutingService domainRoutingService;

  public GravitinoAdminOpsClient(
      GravitinoClientFactory clientFactory,
      GravitinoExceptionMapper exceptionMapper,
      GravitinoDomainRoutingService domainRoutingService) {
    this.clientFactory = clientFactory;
    this.exceptionMapper = exceptionMapper;
    this.domainRoutingService = domainRoutingService;
  }

  public List<GravitinoRoleSummaryResponse> listRoles(String metalake, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return Arrays.stream(client.listRoleNames())
          .sorted()
          .map(roleName -> GravitinoDtoMapper.mapRoleSummary(managedMetalake, roleName))
          .toList();
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public GravitinoRoleResponse getRole(String metalake, String roleName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedRoleName = requiredRoleName(roleName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return GravitinoDtoMapper.mapRole(managedMetalake, client.getRole(normalizedRoleName));
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void createRole(String roleName, String principalUsername) {
    createRole(clientFactory.requiredMetalake(), roleName, principalUsername);
  }

  public void createRole(String metalake, String roleName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedRoleName = requiredRoleName(roleName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client.createRole(normalizedRoleName, Map.of(), List.of());
      } catch (RoleAlreadyExistsException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void deleteRole(String roleName, String principalUsername) {
    deleteRole(clientFactory.requiredMetalake(), roleName, principalUsername);
  }

  public void deleteRole(String metalake, String roleName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedRoleName = requiredRoleName(roleName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client.deleteRole(normalizedRoleName);
      } catch (NotFoundException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public List<GravitinoRolePrivilegeItemResponse> getRolePrivileges(
      String metalake, String roleName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedRoleName = requiredRoleName(roleName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      Role role = client.getRole(normalizedRoleName);
      return GravitinoDtoMapper.mapRolePrivileges(managedMetalake, role);
    } catch (NotFoundException ignored) {
      return List.of();
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void replaceRolePrivileges(
      String metalake,
      String roleName,
      String domain,
      List<GravitinoPrivilegeCommandRequest> commands,
      String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedRoleName = requiredRoleName(roleName);
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      Role role = ensureRoleExists(client, normalizedRoleName);
      revokeRolePrivileges(client, role, normalizedDomain);
      grantPrivileges(client, normalizedRoleName, commands);
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public GravitinoUserResponse getUser(String metalake, String username, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedUsername = requiredUsername(username);
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername)) {
      return GravitinoDtoMapper.mapUser(
          managedMetalake, adminClient.loadMetalake(managedMetalake).getUser(normalizedUsername));
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public List<String> createUser(String username, Long externalUserId, String principalUsername) {
    String managedMetalake = clientFactory.requiredMetalake();
    return createUser(managedMetalake, username, externalUserId, principalUsername)
        ? List.of(managedMetalake)
        : List.of();
  }

  public boolean createUser(
      String metalake, String username, Long externalUserId, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedUsername = requiredUsername(username);
    try (GravitinoAdminClient adminClient =
        clientFactory.createAdminClient(principalUsername, externalUserId)) {
      try {
        adminClient.loadMetalake(managedMetalake).addUser(normalizedUsername);
        return true;
      } catch (UserAlreadyExistsException ignored) {
        return false;
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void deleteUser(String username, String principalUsername) {
    deleteUser(clientFactory.requiredMetalake(), username, principalUsername);
  }

  public void deleteUser(String metalake, String username, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedUsername = requiredUsername(username);
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername)) {
      try {
        adminClient.loadMetalake(managedMetalake).removeUser(normalizedUsername);
      } catch (NotFoundException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public List<String> getUserRoles(String metalake, String username, String principalUsername) {
    GravitinoUserResponse user = getUser(metalake, username, principalUsername);
    return user == null || user.getRoles() == null ? List.of() : user.getRoles();
  }

  public void replaceUserRoles(String username, List<String> roleNames, String principalUsername) {
    replaceUserRoles(clientFactory.requiredMetalake(), username, roleNames, principalUsername);
  }

  public void replaceUserRoles(
      String metalake, String username, List<String> roleNames, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    List<String> normalizedRoleNames = normalizeRoleNames(roleNames);
    String normalizedUsername = requiredUsername(username);
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername)) {
      ensureRolesExist(adminClient, managedMetalake, normalizedRoleNames);
      User user = adminClient.loadMetalake(managedMetalake).getUser(normalizedUsername);
      Set<String> currentRoles =
          user.roles() == null ? new HashSet<>() : new HashSet<>(user.roles());
      Set<String> targetRoles = new HashSet<>(normalizedRoleNames);
      List<String> revokeRoles = new ArrayList<>(currentRoles);
      revokeRoles.removeAll(targetRoles);
      if (!revokeRoles.isEmpty()) {
        adminClient
            .loadMetalake(managedMetalake)
            .revokeRolesFromUser(revokeRoles, normalizedUsername);
      }
      List<String> grantRoles = new ArrayList<>(targetRoles);
      grantRoles.removeAll(currentRoles);
      if (!grantRoles.isEmpty()) {
        adminClient.loadMetalake(managedMetalake).grantRolesToUser(grantRoles, normalizedUsername);
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void revokeAllUserRoles(String username, String principalUsername) {
    replaceUserRoles(username, List.of(), principalUsername);
  }

  public List<GravitinoGroupSummaryResponse> listGroups(String metalake, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return Arrays.stream(client.listGroupNames())
          .sorted()
          .map(groupName -> GravitinoDtoMapper.mapGroupSummary(managedMetalake, groupName))
          .toList();
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public GravitinoGroupResponse getGroup(
      String metalake, String groupName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedGroupName = requireNonBlank(groupName, "groupName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return GravitinoDtoMapper.mapGroup(managedMetalake, client.getGroup(normalizedGroupName));
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void createGroup(String metalake, String groupName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedGroupName = requireNonBlank(groupName, "groupName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client.addGroup(normalizedGroupName);
      } catch (GroupAlreadyExistsException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void deleteGroup(String metalake, String groupName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedGroupName = requireNonBlank(groupName, "groupName");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        client.removeGroup(normalizedGroupName);
      } catch (NotFoundException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public List<String> getGroupRoles(String metalake, String groupName, String principalUsername) {
    GravitinoGroupResponse group = getGroup(metalake, groupName, principalUsername);
    return group == null || group.getRoles() == null ? List.of() : group.getRoles();
  }

  public void replaceGroupRoles(
      String metalake, String groupName, List<String> roleNames, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedGroupName = requireNonBlank(groupName, "groupName");
    List<String> normalizedRoleNames = normalizeRoleNames(roleNames);
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername)) {
      ensureRolesExist(adminClient, managedMetalake, normalizedRoleNames);
      Group group = adminClient.loadMetalake(managedMetalake).getGroup(normalizedGroupName);
      Set<String> currentRoles =
          group.roles() == null ? new HashSet<>() : new HashSet<>(group.roles());
      Set<String> targetRoles = new HashSet<>(normalizedRoleNames);
      List<String> revokeRoles = new ArrayList<>(currentRoles);
      revokeRoles.removeAll(targetRoles);
      if (!revokeRoles.isEmpty()) {
        adminClient
            .loadMetalake(managedMetalake)
            .revokeRolesFromGroup(revokeRoles, normalizedGroupName);
      }
      List<String> grantRoles = new ArrayList<>(targetRoles);
      grantRoles.removeAll(currentRoles);
      if (!grantRoles.isEmpty()) {
        adminClient
            .loadMetalake(managedMetalake)
            .grantRolesToGroup(grantRoles, normalizedGroupName);
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public List<GravitinoRolePrivilegeItemResponse> getUserOverridePrivileges(
      String metalake, Long userId, String username, String principalUsername) {
    String overrideRoleName = overrideRoleName(userId);
    List<GravitinoRolePrivilegeItemResponse> items =
        getRolePrivileges(metalake, overrideRoleName, principalUsername);
    if (items.isEmpty()) {
      ensureUserBoundToOverrideRole(metalake, username, overrideRoleName, principalUsername, false);
    }
    return items;
  }

  public void replaceUserOverridePrivileges(
      String metalake,
      Long userId,
      String username,
      String domain,
      List<GravitinoPrivilegeCommandRequest> commands,
      String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedUsername = requiredUsername(username);
    String overrideRoleName = overrideRoleName(userId);
    replaceRolePrivileges(managedMetalake, overrideRoleName, domain, commands, principalUsername);
    ensureUserBoundToOverrideRole(
        managedMetalake, normalizedUsername, overrideRoleName, principalUsername, true);
  }

  public void clearUserOverridePrivileges(
      String metalake, Long userId, String username, String domain, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedUsername = requiredUsername(username);
    String overrideRoleName = overrideRoleName(userId);
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    boolean revokeRoleBinding = true;
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername);
        GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      try {
        Role role = client.getRole(overrideRoleName);
        revokeRoleBinding = !hasPrivilegesOutsideDomain(role, normalizedDomain);
        revokeRolePrivileges(client, role, normalizedDomain);
      } catch (NotFoundException ignored) {
      }
      try {
        User user = adminClient.loadMetalake(managedMetalake).getUser(normalizedUsername);
        if (revokeRoleBinding && user.roles() != null && user.roles().contains(overrideRoleName)) {
          adminClient
              .loadMetalake(managedMetalake)
              .revokeRolesFromUser(List.of(overrideRoleName), normalizedUsername);
        }
      } catch (NotFoundException ignored) {
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void clearAllUserOverridePrivileges(
      Long userId, String username, String principalUsername) {
    clearUserOverridePrivileges(
        clientFactory.requiredMetalake(),
        userId,
        username,
        GravitinoDomainRoutingService.DOMAIN_ALL,
        principalUsername);
  }

  public GravitinoOwnerResponse getOwner(
      String metalake, String objectType, String fullName, String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    MetadataObject object = parseObject(objectType, fullName);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      return GravitinoDtoMapper.mapOwner(client.getOwner(object));
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  public void setOwner(
      String metalake,
      String objectType,
      String fullName,
      String ownerName,
      String ownerType,
      String principalUsername) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    MetadataObject object = parseObject(objectType, fullName);
    Owner.Type normalizedOwnerType = Owner.Type.valueOf(ownerType.trim().toUpperCase(Locale.ROOT));
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, principalUsername)) {
      client.setOwner(object, requireNonBlank(ownerName, "ownerName"), normalizedOwnerType);
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  private void ensureUserBoundToOverrideRole(
      String metalake,
      String username,
      String overrideRoleName,
      String principalUsername,
      boolean createRoleIfMissing) {
    try (GravitinoAdminClient adminClient = clientFactory.createAdminClient(principalUsername)) {
      if (createRoleIfMissing) {
        try {
          adminClient.loadMetalake(metalake).createRole(overrideRoleName, Map.of(), List.of());
        } catch (RoleAlreadyExistsException ignored) {
        }
      } else {
        try {
          adminClient.loadMetalake(metalake).getRole(overrideRoleName);
        } catch (NotFoundException ignored) {
          return;
        }
      }
      User user = adminClient.loadMetalake(metalake).getUser(username);
      if (user.roles() == null || !user.roles().contains(overrideRoleName)) {
        adminClient.loadMetalake(metalake).grantRolesToUser(List.of(overrideRoleName), username);
      }
    } catch (RuntimeException exception) {
      throw exceptionMapper.map(exception);
    }
  }

  private void ensureRolesExist(
      GravitinoAdminClient adminClient, String metalake, List<String> roleNames) throws Exception {
    for (String roleName : roleNames) {
      try {
        adminClient.loadMetalake(metalake).createRole(roleName, Map.of(), List.of());
      } catch (RoleAlreadyExistsException ignored) {
      }
    }
  }

  private Role ensureRoleExists(GravitinoClient client, String roleName) throws Exception {
    try {
      return client.getRole(roleName);
    } catch (NotFoundException ignored) {
      client.createRole(roleName, Map.of(), List.of());
      return client.getRole(roleName);
    }
  }

  private void revokeRolePrivileges(GravitinoClient client, Role role, String domain)
      throws Exception {
    if (role == null || role.securableObjects() == null) {
      return;
    }
    for (SecurableObject securableObject : role.securableObjects()) {
      if (!domainRoutingService.matchesDomain(
          domain, securableObject.type().name(), securableObject.fullName())) {
        continue;
      }
      Set<Privilege> privileges = new HashSet<>(securableObject.privileges());
      if (privileges.isEmpty()) {
        continue;
      }
      MetadataObject object =
          MetadataObjects.parse(securableObject.fullName(), securableObject.type());
      client.revokePrivilegesFromRole(role.name(), object, privileges);
    }
  }

  private boolean hasPrivilegesOutsideDomain(Role role, String domain) {
    if (role == null || role.securableObjects() == null) {
      return false;
    }
    for (SecurableObject securableObject : role.securableObjects()) {
      if (securableObject.privileges() == null || securableObject.privileges().isEmpty()) {
        continue;
      }
      if (!domainRoutingService.matchesDomain(
          domain, securableObject.type().name(), securableObject.fullName())) {
        return true;
      }
    }
    return false;
  }

  private void grantPrivileges(
      GravitinoClient client, String roleName, List<GravitinoPrivilegeCommandRequest> commands)
      throws Exception {
    if (commands == null || commands.isEmpty()) {
      return;
    }
    for (GravitinoPrivilegeCommandRequest command : commands) {
      String objectType =
          requireNonBlank(command == null ? null : command.getObjectType(), "objectType");
      String objectName =
          requireNonBlank(command == null ? null : command.getObjectName(), "objectName");
      Set<Privilege> privileges =
          parsePrivileges(command == null ? null : command.getPrivilegeCodes());
      if (privileges.isEmpty()) {
        continue;
      }
      for (MetadataObject object :
          buildMetadataObjects(objectType, objectName, command.getColumnNames())) {
        client.grantPrivilegesToRole(roleName, object, privileges);
      }
    }
  }

  private List<MetadataObject> buildMetadataObjects(
      String objectType, String objectName, List<String> columnNames) {
    MetadataObject.Type type = parseObjectType(objectType);
    if (type != MetadataObject.Type.COLUMN) {
      return List.of(MetadataObjects.parse(objectName, type));
    }
    if (columnNames == null || columnNames.isEmpty()) {
      throw new BadRequestException("columnNames is required when objectType is COLUMN");
    }
    List<MetadataObject> objects = new ArrayList<>();
    for (String columnName : columnNames) {
      if (!StringUtils.hasText(columnName)) {
        continue;
      }
      objects.add(
          MetadataObjects.parse(objectName + "." + columnName.trim(), MetadataObject.Type.COLUMN));
    }
    return objects;
  }

  private Set<Privilege> parsePrivileges(List<String> privilegeCodes) {
    Set<Privilege> privileges = new HashSet<>();
    if (privilegeCodes == null) {
      return privileges;
    }
    for (String privilegeCode : privilegeCodes) {
      if (!StringUtils.hasText(privilegeCode)) {
        continue;
      }
      String code = privilegeCode.trim().toUpperCase(Locale.ROOT);
      try {
        privileges.add(Privileges.allow(code));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException(ex, "Unsupported privilege code: %s", code);
      }
    }
    return privileges;
  }

  private MetadataObject.Type parseObjectType(String objectType) {
    try {
      return MetadataObject.Type.valueOf(objectType.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw new BadRequestException(ex, "Unsupported object type: %s", objectType);
    }
  }

  private MetadataObject parseObject(String objectType, String fullName) {
    return MetadataObjects.parse(
        requireNonBlank(fullName, "fullName"),
        parseObjectType(requireNonBlank(objectType, "objectType")));
  }

  private List<String> normalizeRoleNames(List<String> roleNames) {
    if (roleNames == null) {
      return List.of();
    }
    return roleNames.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
  }

  private String overrideRoleName(Long userId) {
    if (userId == null || userId <= 0) {
      throw new BadRequestException("userId must be a positive number");
    }
    return USER_OVERRIDE_ROLE_PREFIX + userId;
  }

  private String requiredUsername(String username) {
    return requireNonBlank(username, "username");
  }

  private String requiredRoleName(String roleName) {
    return requireNonBlank(roleName, "roleName");
  }

  private String requireNonBlank(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      throw new BadRequestException("%s must not be blank", fieldName);
    }
    return value.trim();
  }
}
