/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.server.authorization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.gravitino.Config;
import org.apache.gravitino.Configs;
import org.apache.gravitino.Entity;
import org.apache.gravitino.GravitinoEnv;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.UserPrincipal;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.multitenancy.context.ExternalUserIdContextHolder;
import org.apache.gravitino.multitenancy.context.TenantContext;
import org.apache.gravitino.multitenancy.context.TenantContextHolder;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.PrincipalUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Test of {@link MetadataFilterHelper} */
public class TestMetadataFilterHelper {

  private static MockedStatic<GravitinoEnv> mockedStaticGravitinoEnv;

  @BeforeAll
  public static void setup() {
    mockedStaticGravitinoEnv = mockStatic(GravitinoEnv.class);
    GravitinoEnv gravitinoEnv = mock(GravitinoEnv.class);
    mockedStaticGravitinoEnv.when(GravitinoEnv::getInstance).thenReturn(gravitinoEnv);
    Config configMock = mock(Config.class);
    when(gravitinoEnv.config()).thenReturn(configMock);
    when(configMock.get(eq(Configs.ENABLE_AUTHORIZATION))).thenReturn(true);
  }

  @AfterAll
  public static void stop() {
    if (mockedStaticGravitinoEnv != null) {
      mockedStaticGravitinoEnv.close();
    }
  }

  @Test
  public void testFilter() {
    makeCompletableFutureUseCurrentThread();
    try (MockedStatic<PrincipalUtils> principalUtilsMocked = mockStatic(PrincipalUtils.class);
        MockedStatic<GravitinoAuthorizerProvider> mockStatic =
            mockStatic(GravitinoAuthorizerProvider.class)) {
      principalUtilsMocked
          .when(PrincipalUtils::getCurrentPrincipal)
          .thenReturn(new UserPrincipal("tester"));
      principalUtilsMocked.when(() -> PrincipalUtils.doAs(any(), any())).thenCallRealMethod();

      GravitinoAuthorizerProvider mockedProvider = mock(GravitinoAuthorizerProvider.class);
      mockStatic.when(GravitinoAuthorizerProvider::getInstance).thenReturn(mockedProvider);
      when(mockedProvider.getGravitinoAuthorizer()).thenReturn(new MockGravitinoAuthorizer());
      NameIdentifier[] nameIdentifiers = new NameIdentifier[3];
      nameIdentifiers[0] = NameIdentifierUtil.ofSchema("testMetalake", "testCatalog", "testSchema");
      nameIdentifiers[1] =
          NameIdentifierUtil.ofSchema("testMetalake", "testCatalog", "testSchema2");
      nameIdentifiers[2] =
          NameIdentifierUtil.ofSchema("testMetalake", "testCatalog2", "testSchema");
      NameIdentifier[] filtered =
          MetadataFilterHelper.filterByPrivilege(
              "testMetalake",
              Entity.EntityType.SCHEMA,
              Privilege.Name.USE_SCHEMA.name(),
              nameIdentifiers);
      Assertions.assertEquals(2, filtered.length);
      Assertions.assertEquals("testMetalake.testCatalog.testSchema", filtered[0].toString());
      Assertions.assertEquals("testMetalake.testCatalog2.testSchema", filtered[1].toString());
    }
  }

  @Test
  public void testFilterByExpression() {
    makeCompletableFutureUseCurrentThread();
    try (MockedStatic<PrincipalUtils> principalUtilsMocked = mockStatic(PrincipalUtils.class);
        MockedStatic<GravitinoAuthorizerProvider> mockStatic =
            mockStatic(GravitinoAuthorizerProvider.class)) {
      principalUtilsMocked
          .when(PrincipalUtils::getCurrentPrincipal)
          .thenReturn(new UserPrincipal("tester"));
      principalUtilsMocked.when(() -> PrincipalUtils.doAs(any(), any())).thenCallRealMethod();

      GravitinoAuthorizerProvider mockedProvider = mock(GravitinoAuthorizerProvider.class);
      mockStatic.when(GravitinoAuthorizerProvider::getInstance).thenReturn(mockedProvider);
      when(mockedProvider.getGravitinoAuthorizer()).thenReturn(new MockGravitinoAuthorizer());
      NameIdentifier[] nameIdentifiers = new NameIdentifier[3];
      nameIdentifiers[0] = NameIdentifierUtil.ofSchema("testMetalake", "testCatalog", "testSchema");
      nameIdentifiers[1] =
          NameIdentifierUtil.ofSchema("testMetalake", "testCatalog", "testSchema2");
      nameIdentifiers[2] =
          NameIdentifierUtil.ofSchema("testMetalake", "testCatalog2", "testSchema");
      NameIdentifier[] filtered =
          MetadataFilterHelper.filterByExpression(
              "testMetalake",
              "CATALOG::USE_CATALOG && SCHEMA::USE_SCHEMA",
              Entity.EntityType.SCHEMA,
              nameIdentifiers);
      Assertions.assertEquals(1, filtered.length);
      Assertions.assertEquals("testMetalake.testCatalog.testSchema", filtered[0].toString());
      NameIdentifier[] filtered2 =
          MetadataFilterHelper.filterByExpression(
              "testMetalake", "CATALOG::USE_CATALOG", Entity.EntityType.SCHEMA, nameIdentifiers);
      Assertions.assertEquals(2, filtered2.length);
      Assertions.assertEquals("testMetalake.testCatalog.testSchema", filtered2[0].toString());
      Assertions.assertEquals("testMetalake.testCatalog.testSchema2", filtered2[1].toString());
    }
  }

  @Test
  public void testFilterMetricByExpression() {
    makeCompletableFutureUseCurrentThread();
    try (MockedStatic<PrincipalUtils> principalUtilsMocked = mockStatic(PrincipalUtils.class);
        MockedStatic<GravitinoAuthorizerProvider> mockStatic =
            mockStatic(GravitinoAuthorizerProvider.class)) {
      principalUtilsMocked
          .when(PrincipalUtils::getCurrentPrincipal)
          .thenReturn(new UserPrincipal("tester"));
      principalUtilsMocked.when(() -> PrincipalUtils.doAs(any(), any())).thenCallRealMethod();

      GravitinoAuthorizerProvider mockedProvider = mock(GravitinoAuthorizerProvider.class);
      mockStatic.when(GravitinoAuthorizerProvider::getInstance).thenReturn(mockedProvider);
      when(mockedProvider.getGravitinoAuthorizer()).thenReturn(new MockGravitinoAuthorizer());
      NameIdentifier[] nameIdentifiers = new NameIdentifier[2];
      nameIdentifiers[0] =
          NameIdentifierUtil.ofMetric("testMetalake", "testCatalog", "testSchema", "testMetric");
      nameIdentifiers[1] =
          NameIdentifierUtil.ofMetric("testMetalake", "testCatalog", "testSchema", "otherMetric");
      NameIdentifier[] filtered =
          MetadataFilterHelper.filterByExpression(
              "testMetalake",
              "CATALOG::USE_CATALOG && SCHEMA::USE_SCHEMA && METRIC::USE_METRIC",
              Entity.EntityType.METRIC,
              nameIdentifiers);
      Assertions.assertEquals(1, filtered.length);
      Assertions.assertEquals(
          "testMetalake.testCatalog.testSchema.testMetric", filtered[0].toString());
    }
  }

  @Test
  public void testFilterByExpressionPropagatesTenantAndExternalUserContext() {
    setExecutor(
        runnable -> {
          // Simulate context lost after thread switch.
          TenantContextHolder.remove();
          ExternalUserIdContextHolder.remove();
          runnable.run();
        });
    AtomicReference<TenantContext> observedTenantContext = new AtomicReference<>();
    AtomicReference<String> observedExternalUserId = new AtomicReference<>();
    try (MockedStatic<PrincipalUtils> principalUtilsMocked = mockStatic(PrincipalUtils.class);
        MockedStatic<GravitinoAuthorizerProvider> mockStatic =
            mockStatic(GravitinoAuthorizerProvider.class)) {
      principalUtilsMocked
          .when(PrincipalUtils::getCurrentPrincipal)
          .thenReturn(new UserPrincipal("tester"));
      principalUtilsMocked.when(() -> PrincipalUtils.doAs(any(), any())).thenCallRealMethod();

      GravitinoAuthorizerProvider mockedProvider = mock(GravitinoAuthorizerProvider.class);
      mockStatic.when(GravitinoAuthorizerProvider::getInstance).thenReturn(mockedProvider);
      when(mockedProvider.getGravitinoAuthorizer())
          .thenReturn(
              new MockGravitinoAuthorizer() {
                @Override
                public boolean authorize(
                    java.security.Principal principal,
                    String metalake,
                    org.apache.gravitino.MetadataObject metadataObject,
                    Privilege.Name privilege,
                    org.apache.gravitino.authorization.AuthorizationRequestContext requestContext) {
                  observedTenantContext.set(TenantContextHolder.get());
                  observedExternalUserId.set(ExternalUserIdContextHolder.get());
                  return super.authorize(
                      principal, metalake, metadataObject, privilege, requestContext);
                }
              });

      TenantContextHolder.set(
          TenantContext.builder()
              .withTenantId(1001L)
              .withTenantCode("tenant-test")
              .withTenantName("tenant-test")
              .build());
      ExternalUserIdContextHolder.set("1001");
      NameIdentifier[] nameIdentifiers = {
        NameIdentifierUtil.ofCatalog("testMetalake", "testCatalog")
      };

      NameIdentifier[] filtered =
          MetadataFilterHelper.filterByExpression(
              "testMetalake", "CATALOG::USE_CATALOG", Entity.EntityType.CATALOG, nameIdentifiers);
      Assertions.assertEquals(1, filtered.length);
      Assertions.assertEquals("testMetalake.testCatalog", filtered[0].toString());
      Assertions.assertNotNull(observedTenantContext.get());
      Assertions.assertEquals(1001L, observedTenantContext.get().tenantId());
      Assertions.assertEquals("tenant-test", observedTenantContext.get().tenantCode());
      Assertions.assertEquals("1001", observedExternalUserId.get());
    } finally {
      TenantContextHolder.remove();
      ExternalUserIdContextHolder.remove();
      makeCompletableFutureUseCurrentThread();
    }
  }

  private static void makeCompletableFutureUseCurrentThread() {
    setExecutor(Runnable::run);
  }

  private static void setExecutor(Executor executor) {
    try {
      Class<MetadataFilterHelper> jcasbinAuthorizerClass = MetadataFilterHelper.class;
      Field field = jcasbinAuthorizerClass.getDeclaredField("executor");
      field.setAccessible(true);
      field.set(null, executor);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
