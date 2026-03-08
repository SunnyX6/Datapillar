package com.sunny.datapillar.studio.integration.gravitino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import java.util.List;
import java.util.Map;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.authorization.User;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.client.GravitinoMetalake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GravitinoAdminOpsClientTest {

  @Mock private GravitinoClientFactory clientFactory;
  @Mock private GravitinoExceptionMapper exceptionMapper;
  @Mock private GravitinoClient client;
  @Mock private GravitinoAdminClient adminClient;
  @Mock private GravitinoMetalake metalake;
  @Mock private Role role;
  @Mock private User user;

  private GravitinoAdminOpsClient adminOpsClient;

  @BeforeEach
  void setUp() {
    adminOpsClient =
        new GravitinoAdminOpsClient(
            clientFactory, exceptionMapper, new GravitinoDomainRoutingService(clientFactory));
  }

  @Test
  void createUser_shouldCreateUserInManagedMetalake() {
    when(clientFactory.requiredMetalake()).thenReturn("oneMeta");
    when(clientFactory.requireManagedMetalake("oneMeta")).thenReturn("oneMeta");
    when(clientFactory.createAdminClient(null, 200L)).thenReturn(adminClient);
    when(adminClient.loadMetalake("oneMeta")).thenReturn(metalake);

    assertEquals(List.of("oneMeta"), adminOpsClient.createUser("sunny", 200L, null));

    verify(metalake).addUser("sunny");
  }

  @Test
  void replaceUserOverridePrivileges_shouldBindHiddenRoleToUser() {
    when(clientFactory.requireManagedMetalake("oneMeta")).thenReturn("oneMeta");
    when(clientFactory.createClient("oneMeta", null)).thenReturn(client);
    when(clientFactory.createAdminClient(null)).thenReturn(adminClient);
    when(client.getRole("user_override_99")).thenReturn(role);
    when(adminClient.loadMetalake("oneMeta")).thenReturn(metalake);
    when(metalake.getUser("sunny")).thenReturn(user);
    when(user.roles()).thenReturn(List.of());

    adminOpsClient.replaceUserOverridePrivileges(
        "oneMeta", 99L, "sunny", GravitinoDomainRoutingService.DOMAIN_METADATA, List.of(), null);

    verify(metalake).createRole("user_override_99", Map.of(), List.of());
    verify(metalake).grantRolesToUser(List.of("user_override_99"), "sunny");
  }

  @Test
  void clearUserOverridePrivileges_shouldRevokeHiddenRoleBindingWhenNoPrivilegesRemain() {
    when(clientFactory.requireManagedMetalake("oneMeta")).thenReturn("oneMeta");
    when(clientFactory.createClient("oneMeta", null)).thenReturn(client);
    when(clientFactory.createAdminClient(null)).thenReturn(adminClient);
    when(client.getRole("user_override_99")).thenReturn(role);
    when(adminClient.loadMetalake("oneMeta")).thenReturn(metalake);
    when(metalake.getUser("sunny")).thenReturn(user);
    when(user.roles()).thenReturn(List.of("user_override_99"));

    adminOpsClient.clearUserOverridePrivileges(
        "oneMeta", 99L, "sunny", GravitinoDomainRoutingService.DOMAIN_ALL, null);

    verify(metalake).revokeRolesFromUser(List.of("user_override_99"), "sunny");
  }

  @Test
  void replaceRolePrivileges_shouldOnlyRevokeMatchedDomainWhenMetalakeIsShared() throws Exception {
    SecurableObject metadataObject = org.mockito.Mockito.mock(SecurableObject.class);
    SecurableObject semanticObject = org.mockito.Mockito.mock(SecurableObject.class);
    Privilege metadataPrivilege = org.mockito.Mockito.mock(Privilege.class);
    Privilege semanticPrivilege = org.mockito.Mockito.mock(Privilege.class);

    when(clientFactory.requireManagedMetalake("oneMeta")).thenReturn("oneMeta");
    when(clientFactory.createClient("oneMeta", null)).thenReturn(client);
    when(client.getRole("Platform over management")).thenReturn(role);
    when(role.name()).thenReturn("Platform over management");
    when(role.securableObjects()).thenReturn(List.of(metadataObject, semanticObject));
    when(metadataObject.type()).thenReturn(MetadataObject.Type.TABLE);
    when(metadataObject.fullName()).thenReturn("sales.dwd.orders");
    when(metadataObject.privileges()).thenReturn(List.of(metadataPrivilege));
    when(semanticObject.type()).thenReturn(MetadataObject.Type.CATALOG);
    when(semanticObject.fullName()).thenReturn("OneDS.OneDS.gmv");

    adminOpsClient.replaceRolePrivileges(
        "oneMeta",
        "Platform over management",
        GravitinoDomainRoutingService.DOMAIN_METADATA,
        List.of(),
        null);

    ArgumentCaptor<MetadataObject> objectCaptor = ArgumentCaptor.forClass(MetadataObject.class);
    verify(client)
        .revokePrivilegesFromRole(
            eq("Platform over management"),
            objectCaptor.capture(),
            org.mockito.ArgumentMatchers.<java.util.Set<Privilege>>any());
    assertEquals(MetadataObject.Type.TABLE, objectCaptor.getValue().type());
    assertEquals("sales.dwd.orders", objectCaptor.getValue().fullName());
  }

  @Test
  void clearUserOverridePrivileges_shouldKeepHiddenRoleBindingWhenOtherDomainPrivilegesRemain()
      throws Exception {
    SecurableObject metadataObject = org.mockito.Mockito.mock(SecurableObject.class);
    SecurableObject semanticObject = org.mockito.Mockito.mock(SecurableObject.class);
    Privilege metadataPrivilege = org.mockito.Mockito.mock(Privilege.class);
    Privilege semanticPrivilege = org.mockito.Mockito.mock(Privilege.class);

    when(clientFactory.requireManagedMetalake("oneMeta")).thenReturn("oneMeta");
    when(clientFactory.createClient("oneMeta", null)).thenReturn(client);
    when(clientFactory.createAdminClient(null)).thenReturn(adminClient);
    when(client.getRole("user_override_99")).thenReturn(role);
    when(role.name()).thenReturn("user_override_99");
    when(role.securableObjects()).thenReturn(List.of(metadataObject, semanticObject));
    when(metadataObject.type()).thenReturn(MetadataObject.Type.TABLE);
    when(metadataObject.fullName()).thenReturn("sales.dwd.orders");
    when(metadataObject.privileges()).thenReturn(List.of(metadataPrivilege));
    when(semanticObject.type()).thenReturn(MetadataObject.Type.CATALOG);
    when(semanticObject.fullName()).thenReturn("OneDS.OneDS.gmv");
    when(semanticObject.privileges()).thenReturn(List.of(semanticPrivilege));
    when(adminClient.loadMetalake("oneMeta")).thenReturn(metalake);
    when(metalake.getUser("sunny")).thenReturn(user);

    adminOpsClient.clearUserOverridePrivileges(
        "oneMeta", 99L, "sunny", GravitinoDomainRoutingService.DOMAIN_METADATA, null);

    verify(client)
        .revokePrivilegesFromRole(
            eq("user_override_99"),
            any(),
            org.mockito.ArgumentMatchers.<java.util.Set<Privilege>>any());
    verify(metalake, never()).revokeRolesFromUser(List.of("user_override_99"), "sunny");
  }
}
