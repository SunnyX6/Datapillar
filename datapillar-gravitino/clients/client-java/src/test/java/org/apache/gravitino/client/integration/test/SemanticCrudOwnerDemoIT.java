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
package org.apache.gravitino.client.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.MetadataObjects;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Schema;
import org.apache.gravitino.auth.AuthConstants;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.authorization.Privileges;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.authorization.SecurableObjects;
import org.apache.gravitino.client.GravitinoMetalake;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.exceptions.NoSuchMetadataObjectException;
import org.apache.gravitino.integration.test.util.BaseIT;
import org.apache.gravitino.tag.TagChange;
import org.apache.gravitino.utils.RandomNameUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SemanticCrudOwnerDemoIT extends BaseIT {

  private String metalakeName;

  @Override
  @BeforeAll
  public void startIntegrationTest() throws Exception {
    System.setProperty("gravitino.metrics.jmx.enabled", "false");
    registerCustomConfigs(
        ImmutableMap.of(
            org.apache.gravitino.Configs.ENABLE_AUTHORIZATION.getKey(),
            String.valueOf(true),
            org.apache.gravitino.Configs.SERVICE_ADMINS.getKey(),
            AuthConstants.ANONYMOUS_USER,
            org.apache.gravitino.Configs.AUTHORIZATION_IMPL.getKey(),
            "org.apache.gravitino.server.authorization.PassThroughAuthorizer"));
    super.startIntegrationTest();
    if (client != null) {
      client.close();
    }
    client =
        org.apache.gravitino.client.GravitinoAdminClient.builder(serverUri)
            .withSimpleAuth(AuthConstants.ANONYMOUS_USER)
            .withHeaders(Map.of("X-External-User-Id", "-1"))
            .withTenantContext(1L, "demo_tenant", "Demo Tenant")
            .build();
  }

  @AfterAll
  public void clearMetricsProperty() {
    System.clearProperty("gravitino.metrics.jmx.enabled");
  }

  @AfterEach
  public void cleanupMetalake() throws Exception {
    if (metalakeName != null) {
      try {
        client.dropMetalake(metalakeName, true);
      } catch (Exception ignored) {
        // Ignore cleanup failures in demo teardown.
      }
      metalakeName = null;
    }
  }

  @Test
  public void testSemanticCrudOwnerDemo() throws Exception {
    metalakeName = RandomNameUtils.genRandomName("semantic_demo_metalake");
    String ownerUser = RandomNameUtils.genRandomName("owner_user");
    String roleName = RandomNameUtils.genRandomName("metric_reader");
    String tagName = RandomNameUtils.genRandomName("metric_tag");
    String catalogName = RandomNameUtils.genRandomName("semantic_catalog");
    String schemaName = RandomNameUtils.genRandomName("semantic_schema");

    GravitinoMetalake metalake =
        client.createMetalake(metalakeName, "semantic demo metalake", Collections.emptyMap());
    try (org.apache.gravitino.client.GravitinoAdminClient ownerClient =
        org.apache.gravitino.client.GravitinoAdminClient.builder(serverUri)
            .withSimpleAuth(AuthConstants.ANONYMOUS_USER)
            .withHeaders(Map.of("X-External-User-Id", "1001"))
            .withTenantContext(1L, "demo_tenant", "Demo Tenant")
            .build()) {
      ownerClient.loadMetalake(metalakeName).addUser(ownerUser);
    }

    Catalog catalog =
        metalake.createCatalog(
            catalogName, Catalog.Type.DATASET, "dataset", "semantic demo catalog", Map.of());
    Schema schema =
        catalog
            .asSchemas()
            .createSchema(schemaName, "semantic demo schema", Collections.emptyMap());
    assertNotNull(schema);

    MetadataObject catalogObject =
        MetadataObjects.of(null, catalogName, MetadataObject.Type.CATALOG);
    MetadataObject schemaObject =
        MetadataObjects.of(catalogName, schemaName, MetadataObject.Type.SCHEMA);
    assertOwner(metalake, catalogObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    assertOwner(metalake, schemaObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);

    NameIdentifier wordRootIdent = NameIdentifier.of(schemaName, "country");
    WordRoot createdWordRoot =
        catalog
            .asDatasetCatalog()
            .createWordRoot(wordRootIdent, "country", "Country", "STRING", "comment");
    MetadataObject wordRootObject =
        MetadataObjects.of(
            catalogName + "." + schemaName, createdWordRoot.code(), MetadataObject.Type.WORDROOT);
    assertOwner(metalake, wordRootObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    metalake.setOwner(wordRootObject, ownerUser, Owner.Type.USER);
    assertOwner(metalake, wordRootObject, ownerUser);
    WordRoot loadedWordRoot = catalog.asDatasetCatalog().getWordRoot(wordRootIdent);
    assertEquals("Country", loadedWordRoot.name());
    WordRoot alteredWordRoot =
        catalog.asDatasetCatalog().alterWordRoot(wordRootIdent, "Country New", "STRING", "updated");
    assertEquals("Country New", alteredWordRoot.name());
    assertTrue(catalog.asDatasetCatalog().deleteWordRoot(wordRootIdent));

    NameIdentifier unitIdent = NameIdentifier.of(schemaName, "yuan");
    Unit createdUnit =
        catalog.asDatasetCatalog().createUnit(unitIdent, "yuan", "Yuan", "CNY", "comment");
    MetadataObject unitObject =
        MetadataObjects.of(
            catalogName + "." + schemaName, createdUnit.code(), MetadataObject.Type.UNIT);
    assertOwner(metalake, unitObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    metalake.setOwner(unitObject, ownerUser, Owner.Type.USER);
    assertOwner(metalake, unitObject, ownerUser);
    Unit loadedUnit = catalog.asDatasetCatalog().getUnit(unitIdent);
    assertEquals("yuan", loadedUnit.code());
    Unit alteredUnit =
        catalog.asDatasetCatalog().alterUnit(unitIdent, "Yuan New", "RMB", "updated");
    assertEquals("RMB", alteredUnit.symbol());

    NameIdentifier valueDomainIdent = NameIdentifier.of(schemaName, "country_domain");
    ValueDomain createdValueDomain =
        catalog
            .asDatasetCatalog()
            .createValueDomain(
                valueDomainIdent,
                "country_domain",
                "Country Domain",
                ValueDomain.Type.ENUM,
                ValueDomain.Level.BUSINESS,
                Collections.singletonList(
                    new org.apache.gravitino.dto.dataset.ValueDomainItemDTO("CN", "China")),
                "comment",
                "STRING");
    MetadataObject valueDomainObject =
        MetadataObjects.of(
            catalogName + "." + schemaName,
            createdValueDomain.domainCode(),
            MetadataObject.Type.VALUE_DOMAIN);
    assertOwner(
        metalake, valueDomainObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    metalake.setOwner(valueDomainObject, ownerUser, Owner.Type.USER);
    assertOwner(metalake, valueDomainObject, ownerUser);
    ValueDomain loadedValueDomain = catalog.asDatasetCatalog().getValueDomain(valueDomainIdent);
    assertEquals("country_domain", loadedValueDomain.domainCode());
    ValueDomain alteredValueDomain =
        catalog
            .asDatasetCatalog()
            .alterValueDomain(
                valueDomainIdent,
                "Country Domain New",
                ValueDomain.Level.BUSINESS,
                Collections.singletonList(
                    new org.apache.gravitino.dto.dataset.ValueDomainItemDTO("US", "USA")),
                "updated",
                "STRING");
    assertEquals("country_domain", alteredValueDomain.domainCode());

    NameIdentifier modifierIdent = NameIdentifier.of(schemaName, "region");
    MetricModifier createdModifier =
        catalog.asDatasetCatalog().createMetricModifier(modifierIdent, "region", "comment", "ENUM");
    MetadataObject modifierObject =
        MetadataObjects.of(
            catalogName + "." + schemaName, createdModifier.code(), MetadataObject.Type.MODIFIER);
    assertOwner(metalake, modifierObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    metalake.setOwner(modifierObject, ownerUser, Owner.Type.USER);
    assertOwner(metalake, modifierObject, ownerUser);
    MetricModifier loadedModifier = catalog.asDatasetCatalog().getMetricModifier(modifierIdent);
    assertEquals("region", loadedModifier.code());
    MetricModifier alteredModifier =
        catalog.asDatasetCatalog().alterMetricModifier(modifierIdent, "Region New", "updated");
    assertEquals("Region New", alteredModifier.name());

    NameIdentifier metricIdent = NameIdentifier.of(schemaName, "gmv");
    Metric createdMetric =
        catalog
            .asDatasetCatalog()
            .registerMetric(
                metricIdent,
                "GMV",
                "gmv",
                Metric.Type.ATOMIC,
                "DECIMAL(18,2)",
                "comment",
                Collections.emptyMap(),
                "yuan",
                null,
                "SUM(price)",
                1L,
                null,
                null,
                null,
                "[1]",
                "[2]");
    MetadataObject metricObject =
        MetadataObjects.of(
            catalogName + "." + schemaName, createdMetric.code(), MetadataObject.Type.METRIC);
    assertOwner(metalake, metricObject, org.apache.gravitino.auth.AuthConstants.ANONYMOUS_USER);
    metalake.setOwner(metricObject, ownerUser, Owner.Type.USER);
    assertOwner(metalake, metricObject, ownerUser);

    Metric loadedMetric = catalog.asDatasetCatalog().getMetric(metricIdent);
    assertEquals("gmv", loadedMetric.code());

    Metric alteredMetric =
        catalog.asDatasetCatalog().alterMetric(metricIdent, MetricChange.rename("GMV_NEW"));
    assertEquals("GMV_NEW", alteredMetric.name());

    int[] versions = catalog.asDatasetCatalog().listMetricVersions(metricIdent);
    assertArrayEquals(new int[] {1}, versions);
    MetricVersion metricVersion = catalog.asDatasetCatalog().getMetricVersion(metricIdent, 1);
    assertEquals(1, metricVersion.version());

    MetricVersion alteredVersion =
        catalog
            .asDatasetCatalog()
            .alterMetricVersion(
                metricIdent,
                1,
                "GMV_V2",
                "gmv",
                Metric.Type.ATOMIC.name(),
                "DECIMAL(18,2)",
                "updated",
                "yuan",
                "Yuan",
                null,
                "SUM(price)",
                1L,
                "[1]",
                "[2]");
    assertEquals(2, alteredVersion.version());

    MetricVersion switchedToVersionOne =
        catalog.asDatasetCatalog().switchMetricVersion(metricIdent, 1);
    assertEquals(1, switchedToVersionOne.version());
    MetricVersion switchedVersion = catalog.asDatasetCatalog().switchMetricVersion(metricIdent, 2);
    assertEquals(2, switchedVersion.version());

    metalake.createTag(tagName, "tag comment", Collections.emptyMap());
    String[] associatedTags =
        loadedMetric.supportsTags().associateTags(new String[] {tagName}, null);
    assertEquals(1, associatedTags.length);
    assertEquals(tagName, associatedTags[0]);
    assertEquals(tagName, loadedMetric.supportsTags().getTag(tagName).name());
    metalake.alterTag(tagName, TagChange.updateComment("tag comment updated"));
    assertEquals("tag comment updated", metalake.getTag(tagName).comment());

    SecurableObject metricPrivilegeObject =
        SecurableObjects.parse(
            catalogName + "." + schemaName + "." + createdMetric.code(),
            MetadataObject.Type.METRIC,
            Collections.singletonList(Privileges.UseMetric.allow()));
    Role role =
        metalake.createRole(
            roleName, Collections.emptyMap(), Collections.singletonList(metricPrivilegeObject));
    assertEquals(roleName, role.name());
    String[] bindingRoles = loadedMetric.supportsRoles().listBindingRoleNames();
    assertEquals(1, bindingRoles.length);
    assertEquals(roleName, bindingRoles[0]);
    metalake.revokePrivilegesFromRole(
        roleName, metricObject, Sets.newHashSet(Privileges.UseMetric.allow()));
    assertEquals(0, loadedMetric.supportsRoles().listBindingRoleNames().length);

    assertTrue(catalog.asDatasetCatalog().deleteMetricVersion(metricIdent, 1));
    assertTrue(catalog.asDatasetCatalog().deleteMetric(metricIdent));
    assertTrue(catalog.asDatasetCatalog().deleteMetricModifier(modifierIdent));
    assertTrue(catalog.asDatasetCatalog().deleteUnit(unitIdent));
    assertTrue(catalog.asDatasetCatalog().deleteValueDomain(valueDomainIdent));
    assertTrue(metalake.deleteTag(tagName));
    assertTrue(metalake.deleteRole(roleName));
    assertTrue(metalake.removeUser(ownerUser));
    assertTrue(metalake.dropCatalog(catalogName, true));
    assertTrue(client.dropMetalake(metalakeName, true));
    metalakeName = null;
  }

  private void assertOwner(GravitinoMetalake metalake, MetadataObject object, String expectedOwner)
      throws NoSuchMetadataObjectException {
    Optional<Owner> ownerOptional = metalake.getOwner(object);
    assertTrue(ownerOptional.isPresent(), object.fullName());
    Owner owner = ownerOptional.get();
    assertEquals(expectedOwner, owner.name());
    assertEquals(Owner.Type.USER, owner.type());
  }
}
