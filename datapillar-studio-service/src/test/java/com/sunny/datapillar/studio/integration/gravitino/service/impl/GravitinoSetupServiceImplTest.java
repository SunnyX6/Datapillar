package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoCatalogService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetalakeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRolePrivilegeService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRoleService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoSchemaService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GravitinoSetupServiceImplTest {

  @Mock private GravitinoClientFactory gravitinoClientFactory;
  @Mock private GravitinoMetalakeService gravitinoMetalakeService;
  @Mock private GravitinoCatalogService gravitinoCatalogService;
  @Mock private GravitinoSchemaService gravitinoSchemaService;
  @Mock private GravitinoRolePrivilegeService gravitinoRolePrivilegeService;
  @Mock private GravitinoRoleService gravitinoRoleService;
  @Mock private GravitinoUserService gravitinoUserService;

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void initializeResources_shouldThrowDirectlyWhenSetupFails() {
    when(gravitinoClientFactory.resolveSetupPrincipalUsername()).thenReturn("datapillar");
    when(gravitinoMetalakeService.createMetalake(
            eq("oneMeta"), eq("Datapillar tenant metalake"), any(), eq("datapillar")))
        .thenReturn(true);
    when(gravitinoUserService.createUser("sunny", 200L, "datapillar"))
        .thenReturn(List.of("oneMeta"));
    when(gravitinoCatalogService.createCatalogIfAbsent(eq("oneMeta"), any(), eq("datapillar")))
        .thenReturn(true);
    when(gravitinoSchemaService.createSchemaIfAbsent(
            eq("oneMeta"), eq("OneDS"), any(), eq("datapillar")))
        .thenThrow(new RuntimeException("setup failed"));

    GravitinoSetupServiceImpl service =
        new GravitinoSetupServiceImpl(
            gravitinoClientFactory,
            gravitinoMetalakeService,
            gravitinoCatalogService,
            gravitinoSchemaService,
            gravitinoRolePrivilegeService,
            gravitinoRoleService,
            gravitinoUserService,
            new ObjectMapper());

    assertThrows(
        RuntimeException.class,
        () ->
            service.initializeResources(
                100L, "tenant-acme-data", 200L, "sunny", "Platform over management"));

    verify(gravitinoMetalakeService, never()).dropMetalake("oneMeta", true, "datapillar");
    verify(gravitinoUserService, never()).deleteUser(any(), any(), any());
  }

  @Test
  void initializeResources_shouldProvisionSetupResourcesAndRestoreContext() {
    TenantContextHolder.set(new TenantContext(1L, "origin", 10L, 1L, false));
    when(gravitinoClientFactory.resolveSetupPrincipalUsername()).thenReturn("datapillar");
    when(gravitinoCatalogService.createCatalogIfAbsent(eq("oneMeta"), any(), eq("datapillar")))
        .thenReturn(true);
    when(gravitinoSchemaService.createSchemaIfAbsent(
            eq("oneMeta"), eq("OneDS"), any(), eq("datapillar")))
        .thenReturn(true);

    GravitinoSetupServiceImpl service =
        new GravitinoSetupServiceImpl(
            gravitinoClientFactory,
            gravitinoMetalakeService,
            gravitinoCatalogService,
            gravitinoSchemaService,
            gravitinoRolePrivilegeService,
            gravitinoRoleService,
            gravitinoUserService,
            new ObjectMapper());

    service.initializeResources(
        100L, "tenant-acme-data", 200L, "sunny", "Platform over management");

    InOrder inOrder =
        inOrder(
            gravitinoMetalakeService,
            gravitinoUserService,
            gravitinoRoleService,
            gravitinoCatalogService,
            gravitinoSchemaService,
            gravitinoRolePrivilegeService);
    inOrder
        .verify(gravitinoMetalakeService)
        .createMetalake(eq("oneMeta"), eq("Datapillar tenant metalake"), any(), eq("datapillar"));
    inOrder.verify(gravitinoUserService).createUser("sunny", 200L, "datapillar");
    inOrder.verify(gravitinoRoleService).createRole("Platform over management", "datapillar");
    inOrder
        .verify(gravitinoCatalogService)
        .createCatalogIfAbsent(eq("oneMeta"), any(), eq("datapillar"));
    inOrder
        .verify(gravitinoSchemaService)
        .createSchemaIfAbsent(eq("oneMeta"), eq("OneDS"), any(), eq("datapillar"));
    inOrder
        .verify(gravitinoRolePrivilegeService)
        .replaceRoleDataPrivileges(
            eq("Platform over management"),
            eq(GravitinoDomainRoutingService.DOMAIN_METADATA),
            eq(List.of()),
            eq("datapillar"));
    inOrder
        .verify(gravitinoRolePrivilegeService)
        .replaceRoleDataPrivileges(
            eq("Platform over management"),
            eq(GravitinoDomainRoutingService.DOMAIN_SEMANTIC),
            eq(List.of()),
            eq("datapillar"));
    inOrder
        .verify(gravitinoSchemaService)
        .setSchemaOwner(eq("oneMeta"), eq("OneDS"), eq("OneDS"), eq("sunny"), eq("datapillar"));
    inOrder
        .verify(gravitinoCatalogService)
        .setCatalogOwner(eq("oneMeta"), eq("OneDS"), eq("sunny"), eq("datapillar"));
    inOrder.verify(gravitinoMetalakeService).setMetalakeOwner("oneMeta", "sunny", "datapillar");
    verify(gravitinoClientFactory).resolveSetupPrincipalUsername();

    TenantContext restored = TenantContextHolder.get();
    assertEquals(1L, restored.getTenantId());
    assertEquals("origin", restored.getTenantCode());
  }
}
