package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GravitinoRolePrivilegeServiceImplTest {

  @Mock private GravitinoAdminOpsClient adminOpsClient;
  @Mock private GravitinoClientFactory clientFactory;

  private GravitinoRolePrivilegeServiceImpl service;

  @BeforeEach
  void setUp() {
    lenient().when(clientFactory.requiredMetalake()).thenReturn("oneMeta");
    service =
        new GravitinoRolePrivilegeServiceImpl(
            adminOpsClient, new GravitinoDomainRoutingService(clientFactory));
  }

  @Test
  void getRoleDataPrivileges_shouldSplitDomainsByObjectWhenMetalakeIsShared() {
    GravitinoRolePrivilegeItemResponse metadataItem = new GravitinoRolePrivilegeItemResponse();
    metadataItem.setMetalake("oneMeta");
    metadataItem.setObjectType("TABLE");
    metadataItem.setObjectName("sales.dwd.orders");
    metadataItem.setPrivilegeCode("SELECT_TABLE");

    GravitinoRolePrivilegeItemResponse semanticItem = new GravitinoRolePrivilegeItemResponse();
    semanticItem.setMetalake("oneMeta");
    semanticItem.setObjectType("METRIC");
    semanticItem.setObjectName("OneDS.OneDS.gmv");
    semanticItem.setPrivilegeCode("USE_METRIC");

    when(adminOpsClient.getRolePrivileges("oneMeta", "Developer", null))
        .thenReturn(List.of(metadataItem, semanticItem));

    List<RoleDataPrivilegeItem> items = service.getRoleDataPrivileges("Developer", "ALL", null);

    assertEquals(2, items.size());
    assertEquals("METADATA", items.get(0).getDomain());
    assertEquals("SEMANTIC", items.get(1).getDomain());
  }

  @Test
  void getRoleDataPrivileges_shouldFilterMatchedDomainWhenMetalakeIsShared() {
    GravitinoRolePrivilegeItemResponse metadataItem = new GravitinoRolePrivilegeItemResponse();
    metadataItem.setMetalake("oneMeta");
    metadataItem.setObjectType("TABLE");
    metadataItem.setObjectName("sales.dwd.orders");
    metadataItem.setPrivilegeCode("SELECT_TABLE");

    GravitinoRolePrivilegeItemResponse semanticItem = new GravitinoRolePrivilegeItemResponse();
    semanticItem.setMetalake("oneMeta");
    semanticItem.setObjectType("METRIC");
    semanticItem.setObjectName("OneDS.OneDS.gmv");
    semanticItem.setPrivilegeCode("USE_METRIC");

    when(adminOpsClient.getRolePrivileges("oneMeta", "Developer", null))
        .thenReturn(List.of(metadataItem, semanticItem));

    List<RoleDataPrivilegeItem> items =
        service.getRoleDataPrivileges("Developer", "metadata", null);

    assertEquals(1, items.size());
    assertEquals("METADATA", items.get(0).getDomain());
    assertEquals("sales.dwd.orders", items.get(0).getObjectName());
  }

  @Test
  void replaceRoleDataPrivileges_shouldPassNormalizedDomainToAdminClient() {
    service.replaceRoleDataPrivileges("Developer", "semantic", List.of(), null);

    verify(adminOpsClient)
        .replaceRolePrivileges("oneMeta", "Developer", "SEMANTIC", List.of(), null);
  }
}
