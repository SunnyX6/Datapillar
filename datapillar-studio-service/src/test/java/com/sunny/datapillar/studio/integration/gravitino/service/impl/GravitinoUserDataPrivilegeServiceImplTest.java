package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GravitinoUserDataPrivilegeServiceImplTest {

  @Mock private GravitinoAdminOpsClient adminOpsClient;
  @Mock private GravitinoClientFactory clientFactory;

  private GravitinoUserDataPrivilegeServiceImpl service;

  @BeforeEach
  void setUp() {
    lenient().when(clientFactory.requiredMetalake()).thenReturn("oneMeta");
    service =
        new GravitinoUserDataPrivilegeServiceImpl(
            adminOpsClient, new GravitinoDomainRoutingService(clientFactory));
  }

  @Test
  void replaceUserDataPrivileges_shouldPassNormalizedDomainToAdminClient() {
    service.replaceUserDataPrivileges(99L, "sunny", "semantic", List.of(), null);

    verify(adminOpsClient)
        .replaceUserOverridePrivileges("oneMeta", 99L, "sunny", "SEMANTIC", List.of(), null);
  }

  @Test
  void clearUserDataPrivileges_shouldInvokeOnceWhenMetalakeIsShared() {
    service.clearUserDataPrivileges(99L, "sunny", "ALL", null);

    verify(adminOpsClient).clearUserOverridePrivileges("oneMeta", 99L, "sunny", "ALL", null);
    verifyNoMoreInteractions(adminOpsClient);
  }
}
