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
package org.apache.gravitino.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.Config;
import org.apache.gravitino.Configs;
import org.apache.gravitino.Entity;
import org.apache.gravitino.EntityStore;
import org.apache.gravitino.GravitinoEnv;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.UserPrincipal;
import org.apache.gravitino.authorization.AccessControlDispatcher;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.authorization.OwnerDispatcher;
import org.apache.gravitino.catalog.CatalogManager;
import org.apache.gravitino.catalog.DatasetDispatcher;
import org.apache.gravitino.connector.BaseCatalog;
import org.apache.gravitino.connector.CatalogOperations;
import org.apache.gravitino.connector.authorization.AuthorizationPlugin;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.storage.RandomIdGenerator;
import org.apache.gravitino.storage.memory.TestMemoryEntityStore;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.PrincipalUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestDatasetHookDispatcher {
  private static final String METALAKE = "metalake";
  private static final String USER = "dataset_owner";

  private RecordingDatasetDispatcher datasetDispatcherHandler;
  private DatasetHookDispatcher hookDispatcher;
  private RecordingOwnerDispatcher ownerDispatcher;
  private AccessControlDispatcher accessControlDispatcher;
  private RecordingAuthorizationPlugin authorizationPluginHandler;
  private CatalogManager catalogManager;

  @BeforeEach
  public void setUp() throws Exception {
    datasetDispatcherHandler = new RecordingDatasetDispatcher();
    hookDispatcher = new DatasetHookDispatcher(datasetDispatcherHandler.proxy());
    ownerDispatcher = new RecordingOwnerDispatcher();
    accessControlDispatcher = defaultProxy(AccessControlDispatcher.class);
    authorizationPluginHandler = new RecordingAuthorizationPlugin();
    catalogManager = new StubCatalogManager(new StubCatalog(authorizationPluginHandler.proxy()));

    FieldUtils.writeField(GravitinoEnv.getInstance(), "ownerDispatcher", ownerDispatcher, true);
    FieldUtils.writeField(GravitinoEnv.getInstance(), "accessControlDispatcher", null, true);
    FieldUtils.writeField(GravitinoEnv.getInstance(), "catalogManager", catalogManager, true);
  }

  @Test
  public void testRegisterMetricSetsOwner() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "metric_code");
    Metric metric = defaultProxy(Metric.class);
    datasetDispatcherHandler.setReturnValue("registerMetric", metric);

    Principal principal = new UserPrincipal(USER);
    Metric createdMetric =
        PrincipalUtils.doAs(
            principal,
            () ->
                hookDispatcher.registerMetric(
                    ident,
                    "Gross Merchandise Volume",
                    "gmv",
                    Metric.Type.ATOMIC,
                    "DECIMAL(18,2)",
                    "comment",
                    Collections.emptyMap(),
                    "yuan",
                    new String[] {"parent_metric"},
                    "a+b",
                    1L,
                    "catalog",
                    "schema",
                    "table",
                    "[1]",
                    "[2]"));

    assertSame(metric, createdMetric);
    assertEquals(1, ownerDispatcher.setOwnerCalls);
    assertEquals(METALAKE, ownerDispatcher.lastMetalake);
    assertEquals(
        NameIdentifierUtil.toMetadataObject(ident, Entity.EntityType.METRIC),
        ownerDispatcher.lastMetadataObject);
    assertEquals(USER, ownerDispatcher.lastOwnerName);
    assertEquals(Owner.Type.USER, ownerDispatcher.lastOwnerType);
    assertEquals(0, datasetDispatcherHandler.callCount("deleteMetric"));
  }

  @Test
  public void testCreateMetricModifierSetsOwnerWithModifierType() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "modifier_code");
    MetricModifier modifier = defaultProxy(MetricModifier.class);
    datasetDispatcherHandler.setReturnValue("createMetricModifier", modifier);

    Principal principal = new UserPrincipal(USER);
    MetricModifier createdModifier =
        PrincipalUtils.doAs(
            principal,
            () -> hookDispatcher.createMetricModifier(ident, "region", "comment", "ENUM"));

    assertSame(modifier, createdModifier);
    assertEquals(1, ownerDispatcher.setOwnerCalls);
    assertEquals(
        NameIdentifierUtil.toMetadataObject(ident, Entity.EntityType.MODIFIER),
        ownerDispatcher.lastMetadataObject);
    assertEquals(0, datasetDispatcherHandler.callCount("deleteMetricModifier"));
  }

  @Test
  public void testCreateMetricModifierRollsBackWhenOwnerBindingFails() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "modifier_fail");
    datasetDispatcherHandler.setReturnValue(
        "createMetricModifier", defaultProxy(MetricModifier.class));
    ownerDispatcher.failure = new RuntimeException("owner failed");

    Principal principal = new UserPrincipal(USER);
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                PrincipalUtils.doAs(
                    principal,
                    () -> hookDispatcher.createMetricModifier(ident, "region", "comment", "ENUM")));

    assertTrue(exception.getMessage().contains("owner failed"));
    assertEquals(1, datasetDispatcherHandler.callCount("deleteMetricModifier"));
  }

  @Test
  public void testCreateWordRootSetsOwner() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "wordroot_code");
    WordRoot wordRoot = defaultProxy(WordRoot.class);
    datasetDispatcherHandler.setReturnValue("createWordRoot", wordRoot);

    Principal principal = new UserPrincipal(USER);
    WordRoot createdWordRoot =
        PrincipalUtils.doAs(
            principal,
            () -> hookDispatcher.createWordRoot(ident, "country", "Country", "STRING", "comment"));

    assertSame(wordRoot, createdWordRoot);
    assertEquals(1, ownerDispatcher.setOwnerCalls);
    assertEquals(
        NameIdentifierUtil.toMetadataObject(ident, Entity.EntityType.WORDROOT),
        ownerDispatcher.lastMetadataObject);
  }

  @Test
  public void testCreateUnitSetsOwner() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "unit_code");
    Unit unit = defaultProxy(Unit.class);
    datasetDispatcherHandler.setReturnValue("createUnit", unit);

    Principal principal = new UserPrincipal(USER);
    Unit createdUnit =
        PrincipalUtils.doAs(
            principal, () -> hookDispatcher.createUnit(ident, "yuan", "Yuan", "CNY", "comment"));

    assertSame(unit, createdUnit);
    assertEquals(1, ownerDispatcher.setOwnerCalls);
    assertEquals(
        NameIdentifierUtil.toMetadataObject(ident, Entity.EntityType.UNIT),
        ownerDispatcher.lastMetadataObject);
  }

  @Test
  public void testDeleteMetricModifierRemovesAuthorizationPrivileges() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "modifier_delete");
    datasetDispatcherHandler.setReturnValue("deleteMetricModifier", true);
    FieldUtils.writeField(
        GravitinoEnv.getInstance(), "accessControlDispatcher", accessControlDispatcher, true);

    boolean deleted = hookDispatcher.deleteMetricModifier(ident);

    assertTrue(deleted);
    assertEquals(1, authorizationPluginHandler.metadataUpdatedCalls);
  }

  @Test
  public void testCreateValueDomainSetsOwner() throws Exception {
    NameIdentifier ident = NameIdentifier.of(METALAKE, "dataset", "schema", "country_domain");
    ValueDomain valueDomain = defaultProxy(ValueDomain.class);
    datasetDispatcherHandler.setReturnValue("createValueDomain", valueDomain);

    Principal principal = new UserPrincipal(USER);
    ValueDomain createdValueDomain =
        PrincipalUtils.doAs(
            principal,
            () ->
                hookDispatcher.createValueDomain(
                    ident,
                    "country",
                    "Country",
                    ValueDomain.Type.ENUM,
                    ValueDomain.Level.BUSINESS,
                    Collections.emptyList(),
                    "comment",
                    "STRING"));

    assertSame(valueDomain, createdValueDomain);
    assertEquals(1, ownerDispatcher.setOwnerCalls);
    assertEquals(
        NameIdentifierUtil.toMetadataObject(ident, Entity.EntityType.VALUE_DOMAIN),
        ownerDispatcher.lastMetadataObject);
  }

  private static final class RecordingOwnerDispatcher implements OwnerDispatcher {
    private int setOwnerCalls;
    private String lastMetalake;
    private MetadataObject lastMetadataObject;
    private String lastOwnerName;
    private Owner.Type lastOwnerType;
    private RuntimeException failure;

    @Override
    public void setOwner(
        String metalake, MetadataObject metadataObject, String ownerName, Owner.Type ownerType) {
      setOwnerCalls++;
      if (failure != null) {
        throw failure;
      }
      lastMetalake = metalake;
      lastMetadataObject = metadataObject;
      lastOwnerName = ownerName;
      lastOwnerType = ownerType;
    }

    @Override
    public Optional<Owner> getOwner(String metalake, MetadataObject metadataObject) {
      return Optional.empty();
    }
  }

  private static final class RecordingDatasetDispatcher implements InvocationHandler {
    private final Map<String, Integer> calls = new HashMap<>();
    private final Map<String, Object> returnValues = new HashMap<>();
    private final DatasetDispatcher proxy = defaultProxy(DatasetDispatcher.class, this);

    private DatasetDispatcher proxy() {
      return proxy;
    }

    private void setReturnValue(String methodName, Object value) {
      returnValues.put(methodName, value);
    }

    private int callCount(String methodName) {
      return calls.getOrDefault(methodName, 0);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getDeclaringClass() == Object.class) {
        return handleObjectMethod(proxy, method, args);
      }

      calls.merge(method.getName(), 1, Integer::sum);
      if (returnValues.containsKey(method.getName())) {
        return returnValues.get(method.getName());
      }
      return defaultValue(method.getReturnType());
    }
  }

  private static final class RecordingAuthorizationPlugin implements InvocationHandler {
    private int metadataUpdatedCalls;
    private final AuthorizationPlugin proxy = defaultProxy(AuthorizationPlugin.class, this);

    private AuthorizationPlugin proxy() {
      return proxy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getDeclaringClass() == Object.class) {
        return handleObjectMethod(proxy, method, args);
      }

      if ("onMetadataUpdated".equals(method.getName())) {
        metadataUpdatedCalls++;
        return Boolean.TRUE;
      }

      if ("close".equals(method.getName())) {
        return null;
      }

      return defaultValue(method.getReturnType());
    }
  }

  private static final class StubCatalogManager extends CatalogManager {
    private final Catalog catalog;

    private StubCatalogManager(Catalog catalog) throws IOException {
      super(newConfig(), newStore(), new RandomIdGenerator());
      this.catalog = catalog;
    }

    @Override
    public Catalog loadCatalog(NameIdentifier ident) {
      return catalog;
    }

    private static Config newConfig() {
      Config config = new Config(false) {};
      config.set(Configs.CATALOG_CACHE_EVICTION_INTERVAL_MS, 1000L);
      config.set(Configs.CATALOG_LOAD_ISOLATED, false);
      return config;
    }

    private static EntityStore newStore() throws IOException {
      EntityStore store = new TestMemoryEntityStore.InMemoryEntityStore();
      store.initialize(newConfig());
      return store;
    }
  }

  private static final class StubCatalog extends BaseCatalog<StubCatalog> {
    private final AuthorizationPlugin authorizationPlugin;

    private StubCatalog(AuthorizationPlugin authorizationPlugin) {
      this.authorizationPlugin = authorizationPlugin;
    }

    @Override
    public String shortName() {
      return "dataset";
    }

    @Override
    public Catalog.Type catalogType() {
      return Catalog.Type.DATASET;
    }

    @Override
    protected CatalogOperations newOps(Map<String, String> config) {
      return null;
    }

    @Override
    public AuthorizationPlugin getAuthorizationPlugin() {
      return authorizationPlugin;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T defaultProxy(Class<T> type) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(), new Class<?>[] {type}, TestDatasetHookDispatcher::defaultInvoke);
  }

  @SuppressWarnings("unchecked")
  private static <T> T defaultProxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultInvoke(Object proxy, Method method, Object[] args) {
    if (method.getDeclaringClass() == Object.class) {
      return handleObjectMethod(proxy, method, args);
    }
    return defaultValue(method.getReturnType());
  }

  private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
    switch (method.getName()) {
      case "toString":
        return proxy.getClass().getName();
      case "hashCode":
        return System.identityHashCode(proxy);
      case "equals":
        return proxy == args[0];
      default:
        return null;
    }
  }

  private static Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == float.class) {
      return 0F;
    }
    if (returnType == double.class) {
      return 0D;
    }
    if (returnType == char.class) {
      return '\0';
    }
    return null;
  }
}
