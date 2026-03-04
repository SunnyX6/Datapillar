package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.gravitino.error.GravitinoErrorMapper;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Privileges;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.exceptions.NoSuchRoleException;

/** Gravitino security transport client. */
public class GravitinoSecurityClient {

  private final GravitinoSdkClientFactory clientFactory;
  private final ObjectMapper objectMapper;
  private final GravitinoErrorMapper errorMapper;

  public GravitinoSecurityClient(
      GravitinoSdkClientFactory clientFactory,
      ObjectMapper objectMapper,
      GravitinoErrorMapper errorMapper) {
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
  }

  public JsonNode syncUser(String username, ConnectorContext context) {
    if (username == null || username.isBlank()) {
      throw new ConnectorException(ErrorType.BAD_REQUEST, "username must not be blank");
    }
    try (GravitinoClient client = clientFactory.createMetadataClient(context)) {
      try {
        client.addUser(username.trim());
      } catch (org.apache.gravitino.exceptions.UserAlreadyExistsException ignored) {
        // idempotent by design
      }
      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      response.put("username", username.trim());
      return response;
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  public JsonNode listRoleDataPrivileges(String roleName, String domain, ConnectorContext context) {
    if (roleName == null || roleName.isBlank()) {
      throw new ConnectorException(ErrorType.BAD_REQUEST, "roleName must not be blank");
    }

    try (GravitinoClient client = clientFactory.createMetadataClient(context)) {
      Role role = client.getRole(roleName.trim());
      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      var items = objectMapper.createArrayNode();
      for (SecurableObject securableObject : role.securableObjects()) {
        for (Privilege privilege : securableObject.privileges()) {
          var item = objectMapper.createObjectNode();
          item.put("domain", defaultDomain(domain));
          item.put("objectType", securableObject.type().name());
          item.put("objectName", securableObject.fullName());
          if (securableObject.type() == MetadataObject.Type.COLUMN) {
            String[] parts = securableObject.fullName().split("\\.");
            if (parts.length >= 4) {
              item.put("columnName", parts[parts.length - 1]);
              item.put(
                  "objectName", String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1)));
            }
          }
          item.put("privilegeCode", privilege.name().name());
          items.add(item);
        }
      }
      response.set("items", items);
      return response;
    } catch (NoSuchRoleException noSuchRoleException) {
      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      response.set("items", objectMapper.createArrayNode());
      return response;
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  public JsonNode syncRoleDataPrivileges(
      String roleName, String domain, JsonNode commandsNode, ConnectorContext context) {
    if (roleName == null || roleName.isBlank()) {
      throw new ConnectorException(ErrorType.BAD_REQUEST, "roleName must not be blank");
    }

    try (GravitinoClient client = clientFactory.createMetadataClient(context)) {
      ensureRoleExists(client, roleName.trim());
      revokeAllPrivileges(client, roleName.trim());
      grantPrivileges(client, roleName.trim(), commandsNode);

      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      response.put("domain", defaultDomain(domain));
      return response;
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  private void ensureRoleExists(GravitinoClient client, String roleName) throws Exception {
    try {
      client.getRole(roleName);
    } catch (NoSuchRoleException ignored) {
      client.createRole(roleName, Map.of(), List.of());
    }
  }

  private void revokeAllPrivileges(GravitinoClient client, String roleName) throws Exception {
    Role role = client.getRole(roleName);
    for (SecurableObject securableObject : role.securableObjects()) {
      Set<Privilege> privileges = new HashSet<>(securableObject.privileges());
      if (privileges.isEmpty()) {
        continue;
      }
      MetadataObject object =
          MetadataObjects.parse(securableObject.fullName(), securableObject.type());
      client.revokePrivilegesFromRole(roleName, object, privileges);
    }
  }

  private void grantPrivileges(GravitinoClient client, String roleName, JsonNode commandsNode)
      throws Exception {
    if (commandsNode == null || !commandsNode.isArray()) {
      return;
    }
    for (JsonNode command : commandsNode) {
      String objectType = requiredText(command, "objectType");
      String objectName = requiredText(command, "objectName");
      Set<Privilege> privileges = parsePrivileges(command.path("privilegeCodes"));
      if (privileges.isEmpty()) {
        continue;
      }

      List<MetadataObject> objects =
          buildMetadataObjects(objectType, objectName, command.path("columnNames"));
      for (MetadataObject object : objects) {
        client.grantPrivilegesToRole(roleName, object, privileges);
      }
    }
  }

  private List<MetadataObject> buildMetadataObjects(
      String objectType, String objectName, JsonNode columnNamesNode) {
    MetadataObject.Type type = parseObjectType(objectType);
    if (type != MetadataObject.Type.COLUMN) {
      return List.of(MetadataObjects.parse(objectName, type));
    }

    List<MetadataObject> objects = new ArrayList<>();
    if (columnNamesNode == null || !columnNamesNode.isArray() || columnNamesNode.isEmpty()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "columnNames is required when objectType is COLUMN");
    }
    for (JsonNode columnNode : columnNamesNode) {
      if (columnNode == null || columnNode.asText().isBlank()) {
        continue;
      }
      objects.add(
          MetadataObjects.parse(
              objectName + "." + columnNode.asText().trim(), MetadataObject.Type.COLUMN));
    }
    return objects;
  }

  private Set<Privilege> parsePrivileges(JsonNode privilegeCodesNode) {
    Set<Privilege> privileges = new HashSet<>();
    if (privilegeCodesNode == null || !privilegeCodesNode.isArray()) {
      return privileges;
    }
    for (JsonNode privilegeCodeNode : privilegeCodesNode) {
      if (privilegeCodeNode == null || privilegeCodeNode.asText().isBlank()) {
        continue;
      }
      String code = privilegeCodeNode.asText().trim().toUpperCase(Locale.ROOT);
      try {
        privileges.add(Privileges.allow(code));
      } catch (IllegalArgumentException ex) {
        throw new ConnectorException(
            ErrorType.BAD_REQUEST, "Unsupported privilege code: " + code, ex);
      }
    }
    return privileges;
  }

  private MetadataObject.Type parseObjectType(String objectType) {
    try {
      return MetadataObject.Type.valueOf(objectType.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Unsupported object type: " + objectType, ex);
    }
  }

  private String requiredText(JsonNode node, String field) {
    String value = node == null ? null : node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw new ConnectorException(ErrorType.BAD_REQUEST, "Missing required field: " + field);
    }
    return value.trim();
  }

  private String defaultDomain(String domain) {
    if (domain == null || domain.isBlank()) {
      return "METADATA";
    }
    return domain.trim().toUpperCase(Locale.ROOT);
  }
}
