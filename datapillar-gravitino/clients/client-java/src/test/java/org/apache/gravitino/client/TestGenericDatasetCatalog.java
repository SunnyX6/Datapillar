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
package org.apache.gravitino.client;

import static org.apache.hc.core5.http.HttpStatus.SC_CONFLICT;
import static org.apache.hc.core5.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.dto.AuditDTO;
import org.apache.gravitino.dto.CatalogDTO;
import org.apache.gravitino.dto.dataset.MetricDTO;
import org.apache.gravitino.dto.dataset.MetricModifierDTO;
import org.apache.gravitino.dto.dataset.MetricVersionDTO;
import org.apache.gravitino.dto.dataset.UnitDTO;
import org.apache.gravitino.dto.dataset.ValueDomainDTO;
import org.apache.gravitino.dto.dataset.ValueDomainItemDTO;
import org.apache.gravitino.dto.dataset.WordRootDTO;
import org.apache.gravitino.dto.requests.CatalogCreateRequest;
import org.apache.gravitino.dto.requests.MetricModifierCreateRequest;
import org.apache.gravitino.dto.requests.MetricModifierUpdateRequest;
import org.apache.gravitino.dto.requests.MetricRegisterRequest;
import org.apache.gravitino.dto.requests.MetricSwitchVersionRequest;
import org.apache.gravitino.dto.requests.MetricUpdateRequest;
import org.apache.gravitino.dto.requests.MetricUpdatesRequest;
import org.apache.gravitino.dto.requests.MetricVersionUpdateRequest;
import org.apache.gravitino.dto.requests.UnitCreateRequest;
import org.apache.gravitino.dto.requests.UnitUpdateRequest;
import org.apache.gravitino.dto.requests.ValueDomainCreateRequest;
import org.apache.gravitino.dto.requests.ValueDomainUpdateRequest;
import org.apache.gravitino.dto.requests.WordRootCreateRequest;
import org.apache.gravitino.dto.requests.WordRootUpdateRequest;
import org.apache.gravitino.dto.responses.CatalogResponse;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.ErrorResponse;
import org.apache.gravitino.dto.responses.MetricListResponse;
import org.apache.gravitino.dto.responses.MetricModifierListResponse;
import org.apache.gravitino.dto.responses.MetricModifierResponse;
import org.apache.gravitino.dto.responses.MetricResponse;
import org.apache.gravitino.dto.responses.MetricVersionListResponse;
import org.apache.gravitino.dto.responses.MetricVersionResponse;
import org.apache.gravitino.dto.responses.UnitListResponse;
import org.apache.gravitino.dto.responses.UnitResponse;
import org.apache.gravitino.dto.responses.ValueDomainListResponse;
import org.apache.gravitino.dto.responses.ValueDomainResponse;
import org.apache.gravitino.dto.responses.WordRootListResponse;
import org.apache.gravitino.dto.responses.WordRootResponse;
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchUnitException;
import org.apache.gravitino.exceptions.NoSuchValueDomainException;
import org.apache.gravitino.exceptions.NoSuchWordRootException;
import org.apache.gravitino.exceptions.UnitAlreadyExistsException;
import org.apache.gravitino.exceptions.ValueDomainAlreadyExistsException;
import org.apache.gravitino.exceptions.WordRootAlreadyExistsException;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGenericDatasetCatalog extends TestBase {

  private static final String METALAKE_NAME = "metalake_for_dataset_test";
  private static final String CATALOG_NAME = "catalog_for_dataset_test";
  private static final String PROVIDER = "test";

  private static Catalog catalog;

  @BeforeAll
  public static void setUp() throws Exception {
    TestBase.setUp();

    GravitinoMetalake metalake = TestGravitinoMetalake.createMetalake(client, METALAKE_NAME);

    CatalogDTO mockCatalog =
        CatalogDTO.builder()
            .withName(CATALOG_NAME)
            .withType(Catalog.Type.DATASET)
            .withProvider(PROVIDER)
            .withComment("comment")
            .withProperties(Collections.emptyMap())
            .withAudit(audit())
            .build();

    CatalogCreateRequest request =
        new CatalogCreateRequest(
            CATALOG_NAME, Catalog.Type.DATASET, PROVIDER, "comment", Collections.emptyMap());
    CatalogResponse response = new CatalogResponse(mockCatalog);
    buildMockResource(
        Method.POST, "/api/metalakes/" + METALAKE_NAME + "/catalogs", request, response, SC_OK);

    catalog =
        metalake.createCatalog(
            CATALOG_NAME, Catalog.Type.DATASET, PROVIDER, "comment", Collections.emptyMap());
  }

  @Test
  public void testListAndGetMetric() throws JsonProcessingException {
    NameIdentifier metricIdent = NameIdentifier.of("schema1", "metric_code");
    Namespace metricFullNs = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String metricPath = withSlash(GenericDatasetCatalog.formatMetricRequestPath(metricFullNs));

    MetricDTO metricDTO = mockMetricDTO("GMV", metricIdent.name());
    MetricListResponse listResponse = new MetricListResponse(new MetricDTO[] {metricDTO}, 1, 0, 20);
    buildMockResource(
        Method.GET,
        metricPath,
        ImmutableMap.of("offset", "0", "limit", "20"),
        null,
        listResponse,
        SC_OK);

    PagedResult<Metric> metrics =
        catalog.asDatasetCatalog().listMetrics(metricIdent.namespace(), 0, 20);
    Assertions.assertEquals(1, metrics.total());
    Assertions.assertEquals(metricIdent.name(), metrics.items().get(0).code());
    Assertions.assertInstanceOf(GenericMetric.class, metrics.items().get(0));

    MetricResponse metricResponse = new MetricResponse(metricDTO);
    buildMockResource(
        Method.GET, metricPath + "/" + metricIdent.name(), null, metricResponse, SC_OK);
    Metric metric = catalog.asDatasetCatalog().getMetric(metricIdent);
    Assertions.assertEquals(metricIdent.name(), metric.code());
    Assertions.assertInstanceOf(GenericMetric.class, metric);

    ErrorResponse notFound =
        ErrorResponse.notFound(NoSuchMetricException.class.getSimpleName(), "metric not found");
    buildMockResource(
        Method.GET, metricPath + "/" + metricIdent.name(), null, notFound, SC_NOT_FOUND);
    Assertions.assertThrows(
        NoSuchMetricException.class, () -> catalog.asDatasetCatalog().getMetric(metricIdent));
  }

  @Test
  public void testRegisterAlterAndDeleteMetric() throws JsonProcessingException {
    NameIdentifier metricIdent = NameIdentifier.of("schema1", "metric_code");
    Namespace metricFullNs = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String metricPath = withSlash(GenericDatasetCatalog.formatMetricRequestPath(metricFullNs));

    MetricRegisterRequest registerRequest =
        new MetricRegisterRequest(
            "GMV",
            "metric_code",
            Metric.Type.ATOMIC,
            "DECIMAL(18,2)",
            "comment",
            ImmutableMap.of("k1", "v1"),
            "CNY",
            null,
            "SUM(price)",
            1L,
            "ref_catalog",
            "ref_schema",
            "ref_table",
            "[1]",
            "[2]");
    MetricResponse registerResponse = new MetricResponse(mockMetricDTO("GMV", metricIdent.name()));
    buildMockResource(Method.POST, metricPath, registerRequest, registerResponse, SC_OK);

    Metric registeredMetric =
        catalog
            .asDatasetCatalog()
            .registerMetric(
                metricIdent,
                "GMV",
                "metric_code",
                Metric.Type.ATOMIC,
                "DECIMAL(18,2)",
                "comment",
                ImmutableMap.of("k1", "v1"),
                "CNY",
                null,
                "SUM(price)",
                1L,
                "ref_catalog",
                "ref_schema",
                "ref_table",
                "[1]",
                "[2]");
    Assertions.assertEquals(metricIdent.name(), registeredMetric.code());
    Assertions.assertInstanceOf(GenericMetric.class, registeredMetric);

    MetricUpdateRequest updateRequest = new MetricUpdateRequest.RenameMetricRequest("GMV_NEW");
    MetricUpdatesRequest updatesRequest = new MetricUpdatesRequest(ImmutableList.of(updateRequest));
    MetricResponse alterResponse = new MetricResponse(mockMetricDTO("GMV_NEW", metricIdent.name()));
    buildMockResource(
        Method.PUT, metricPath + "/" + metricIdent.name(), updatesRequest, alterResponse, SC_OK);
    Metric alteredMetric =
        catalog.asDatasetCatalog().alterMetric(metricIdent, MetricChange.rename("GMV_NEW"));
    Assertions.assertEquals("GMV_NEW", alteredMetric.name());
    Assertions.assertInstanceOf(GenericMetric.class, alteredMetric);

    DropResponse dropResponse = new DropResponse(true);
    buildMockResource(
        Method.DELETE, metricPath + "/" + metricIdent.name(), null, dropResponse, SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteMetric(metricIdent));

    ErrorResponse alreadyExists =
        ErrorResponse.alreadyExists(
            MetricAlreadyExistsException.class.getSimpleName(), "metric exists");
    buildMockResource(Method.POST, metricPath, registerRequest, alreadyExists, SC_CONFLICT);
    Assertions.assertThrows(
        MetricAlreadyExistsException.class,
        () ->
            catalog
                .asDatasetCatalog()
                .registerMetric(
                    metricIdent,
                    "GMV",
                    "metric_code",
                    Metric.Type.ATOMIC,
                    "DECIMAL(18,2)",
                    "comment",
                    ImmutableMap.of("k1", "v1"),
                    "CNY",
                    null,
                    "SUM(price)",
                    1L,
                    "ref_catalog",
                    "ref_schema",
                    "ref_table",
                    "[1]",
                    "[2]"));
  }

  @Test
  public void testMetricVersionOperations() throws JsonProcessingException {
    NameIdentifier metricIdent = NameIdentifier.of("schema1", "metric_code");
    NameIdentifier metricFullIdent =
        NameIdentifier.of(METALAKE_NAME, CATALOG_NAME, "schema1", "metric_code");
    String metricVersionPath =
        withSlash(GenericDatasetCatalog.formatMetricVersionRequestPath(metricFullIdent));

    MetricVersionListResponse versionListResponse = new MetricVersionListResponse(new int[] {1, 2});
    buildMockResource(
        Method.GET, metricVersionPath + "/versions", null, versionListResponse, SC_OK);
    int[] versions = catalog.asDatasetCatalog().listMetricVersions(metricIdent);
    Assertions.assertArrayEquals(new int[] {1, 2}, versions);

    MetricVersionDTO version1 = mockMetricVersionDTO(1, "GMV", metricIdent.name());
    buildMockResource(
        Method.GET,
        metricVersionPath + "/versions/1",
        null,
        new MetricVersionResponse(version1),
        SC_OK);
    MetricVersion loadedVersion = catalog.asDatasetCatalog().getMetricVersion(metricIdent, 1);
    Assertions.assertEquals(1, loadedVersion.version());
    Assertions.assertInstanceOf(GenericMetricVersion.class, loadedVersion);

    buildMockResource(
        Method.GET, metricVersionPath + "/versions", null, versionListResponse, SC_OK);
    buildMockResource(
        Method.GET,
        metricVersionPath + "/versions/1",
        null,
        new MetricVersionResponse(version1),
        SC_OK);
    buildMockResource(
        Method.GET,
        metricVersionPath + "/versions/2",
        null,
        new MetricVersionResponse(mockMetricVersionDTO(2, "GMV", metricIdent.name())),
        SC_OK);
    MetricVersion[] versionInfos = catalog.asDatasetCatalog().listMetricVersionInfos(metricIdent);
    Assertions.assertEquals(2, versionInfos.length);
    Assertions.assertEquals(2, versionInfos[1].version());
    Assertions.assertInstanceOf(GenericMetricVersion.class, versionInfos[0]);
    Assertions.assertInstanceOf(GenericMetricVersion.class, versionInfos[1]);

    MetricSwitchVersionRequest switchRequest = new MetricSwitchVersionRequest(2);
    buildMockResource(
        Method.PUT,
        metricVersionPath + "/switch/versions/2",
        switchRequest,
        new MetricVersionResponse(mockMetricVersionDTO(2, "GMV", metricIdent.name())),
        SC_OK);
    MetricVersion switched = catalog.asDatasetCatalog().switchMetricVersion(metricIdent, 2);
    Assertions.assertEquals(2, switched.version());
    Assertions.assertInstanceOf(GenericMetricVersion.class, switched);

    MetricVersionUpdateRequest alterVersionRequest =
        new MetricVersionUpdateRequest(
            "GMV_NEW",
            "metric_code",
            Metric.Type.ATOMIC.name(),
            "DECIMAL(18,2)",
            "updated",
            "CNY",
            "Yuan",
            null,
            "SUM(price)",
            1L,
            "[1]",
            "[2]");
    buildMockResource(
        Method.PUT,
        metricVersionPath + "/versions/1",
        alterVersionRequest,
        new MetricVersionResponse(mockMetricVersionDTO(3, "GMV_NEW", metricIdent.name())),
        SC_OK);
    MetricVersion alteredVersion =
        catalog
            .asDatasetCatalog()
            .alterMetricVersion(
                metricIdent,
                1,
                "GMV_NEW",
                "metric_code",
                Metric.Type.ATOMIC.name(),
                "DECIMAL(18,2)",
                "updated",
                "CNY",
                "Yuan",
                null,
                "SUM(price)",
                1L,
                "[1]",
                "[2]");
    Assertions.assertEquals(3, alteredVersion.version());
    Assertions.assertInstanceOf(GenericMetricVersion.class, alteredVersion);

    buildMockResource(
        Method.DELETE, metricVersionPath + "/versions/1", null, new DropResponse(true), SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteMetricVersion(metricIdent, 1));

    ErrorResponse noSuchVersion =
        ErrorResponse.notFound(
            NoSuchMetricVersionException.class.getSimpleName(), "version not found");
    buildMockResource(
        Method.GET, metricVersionPath + "/versions/1", null, noSuchVersion, SC_NOT_FOUND);
    Assertions.assertThrows(
        NoSuchMetricVersionException.class,
        () -> catalog.asDatasetCatalog().getMetricVersion(metricIdent, 1));
  }

  @Test
  public void testMetricModifierOperations() throws JsonProcessingException {
    NameIdentifier modifierIdent = NameIdentifier.of("schema1", "region");
    Namespace namespace = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String modifierPath = withSlash(GenericDatasetCatalog.formatModifierRequestPath(namespace));

    MetricModifierDTO modifierDTO = mockMetricModifierDTO("Region", modifierIdent.name());
    MetricModifierListResponse listResponse =
        new MetricModifierListResponse(new MetricModifierDTO[] {modifierDTO}, 1, 0, 20);
    buildMockResource(
        Method.GET,
        modifierPath,
        ImmutableMap.of("offset", "0", "limit", "20"),
        null,
        listResponse,
        SC_OK);
    PagedResult<MetricModifier> modifiers =
        catalog.asDatasetCatalog().listMetricModifiers(modifierIdent.namespace(), 0, 20);
    Assertions.assertEquals(1, modifiers.total());
    Assertions.assertEquals(modifierIdent.name(), modifiers.items().get(0).code());
    Assertions.assertInstanceOf(GenericMetricModifier.class, modifiers.items().get(0));

    buildMockResource(
        Method.GET,
        modifierPath + "/" + modifierIdent.name(),
        null,
        new MetricModifierResponse(modifierDTO),
        SC_OK);
    MetricModifier loaded = catalog.asDatasetCatalog().getMetricModifier(modifierIdent);
    Assertions.assertEquals(modifierIdent.name(), loaded.code());
    Assertions.assertInstanceOf(GenericMetricModifier.class, loaded);

    MetricModifierCreateRequest createRequest =
        new MetricModifierCreateRequest(
            modifierIdent.name(), modifierIdent.name(), "comment", "ENUM");
    buildMockResource(
        Method.POST, modifierPath, createRequest, new MetricModifierResponse(modifierDTO), SC_OK);
    MetricModifier created =
        catalog
            .asDatasetCatalog()
            .createMetricModifier(modifierIdent, modifierIdent.name(), "comment", "ENUM");
    Assertions.assertEquals(modifierIdent.name(), created.code());
    Assertions.assertInstanceOf(GenericMetricModifier.class, created);

    MetricModifierUpdateRequest updateRequest =
        new MetricModifierUpdateRequest("Region New", "updated");
    buildMockResource(
        Method.PUT,
        modifierPath + "/" + modifierIdent.name(),
        updateRequest,
        new MetricModifierResponse(mockMetricModifierDTO("Region New", modifierIdent.name())),
        SC_OK);
    MetricModifier altered =
        catalog.asDatasetCatalog().alterMetricModifier(modifierIdent, "Region New", "updated");
    Assertions.assertEquals("Region New", altered.name());
    Assertions.assertInstanceOf(GenericMetricModifier.class, altered);

    buildMockResource(
        Method.DELETE,
        modifierPath + "/" + modifierIdent.name(),
        null,
        new DropResponse(true),
        SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteMetricModifier(modifierIdent));
  }

  @Test
  public void testWordRootOperations() throws JsonProcessingException {
    NameIdentifier rootIdent = NameIdentifier.of("schema1", "country");
    Namespace namespace = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String rootPath = withSlash(GenericDatasetCatalog.formatWordRootRequestPath(namespace));

    WordRootDTO rootDTO = mockWordRootDTO(rootIdent.name(), "Country");
    WordRootListResponse listResponse =
        new WordRootListResponse(new WordRootDTO[] {rootDTO}, 1, 0, 20);
    buildMockResource(
        Method.GET,
        rootPath,
        ImmutableMap.of("offset", "0", "limit", "20"),
        null,
        listResponse,
        SC_OK);
    PagedResult<WordRoot> roots =
        catalog.asDatasetCatalog().listWordRoots(rootIdent.namespace(), 0, 20);
    Assertions.assertEquals(1, roots.total());
    Assertions.assertEquals(rootIdent.name(), roots.items().get(0).code());
    Assertions.assertInstanceOf(GenericWordRoot.class, roots.items().get(0));

    buildMockResource(
        Method.GET, rootPath + "/" + rootIdent.name(), null, new WordRootResponse(rootDTO), SC_OK);
    WordRoot loaded = catalog.asDatasetCatalog().getWordRoot(rootIdent);
    Assertions.assertEquals(rootIdent.name(), loaded.code());
    Assertions.assertInstanceOf(GenericWordRoot.class, loaded);

    WordRootCreateRequest createRequest =
        new WordRootCreateRequest(rootIdent.name(), "Country", "STRING", "comment");
    buildMockResource(Method.POST, rootPath, createRequest, new WordRootResponse(rootDTO), SC_OK);
    WordRoot created =
        catalog
            .asDatasetCatalog()
            .createWordRoot(rootIdent, rootIdent.name(), "Country", "STRING", "comment");
    Assertions.assertEquals(rootIdent.name(), created.code());
    Assertions.assertInstanceOf(GenericWordRoot.class, created);

    WordRootUpdateRequest updateRequest =
        new WordRootUpdateRequest("Country New", "STRING", "updated");
    buildMockResource(
        Method.PUT,
        rootPath + "/" + rootIdent.name(),
        updateRequest,
        new WordRootResponse(mockWordRootDTO(rootIdent.name(), "Country New")),
        SC_OK);
    WordRoot altered =
        catalog.asDatasetCatalog().alterWordRoot(rootIdent, "Country New", "STRING", "updated");
    Assertions.assertEquals("Country New", altered.name());
    Assertions.assertInstanceOf(GenericWordRoot.class, altered);

    buildMockResource(
        Method.DELETE, rootPath + "/" + rootIdent.name(), null, new DropResponse(true), SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteWordRoot(rootIdent));

    ErrorResponse alreadyExists =
        ErrorResponse.alreadyExists(
            WordRootAlreadyExistsException.class.getSimpleName(), "word root exists");
    buildMockResource(Method.POST, rootPath, createRequest, alreadyExists, SC_CONFLICT);
    Assertions.assertThrows(
        WordRootAlreadyExistsException.class,
        () ->
            catalog
                .asDatasetCatalog()
                .createWordRoot(rootIdent, rootIdent.name(), "Country", "STRING", "comment"));

    ErrorResponse notFound =
        ErrorResponse.notFound(
            NoSuchWordRootException.class.getSimpleName(), "word root not found");
    buildMockResource(Method.GET, rootPath + "/" + rootIdent.name(), null, notFound, SC_NOT_FOUND);
    Assertions.assertThrows(
        NoSuchWordRootException.class, () -> catalog.asDatasetCatalog().getWordRoot(rootIdent));
  }

  @Test
  public void testUnitOperations() throws JsonProcessingException {
    NameIdentifier unitIdent = NameIdentifier.of("schema1", "yuan");
    Namespace namespace = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String unitPath = withSlash(GenericDatasetCatalog.formatUnitRequestPath(namespace));

    UnitDTO unitDTO = mockUnitDTO(unitIdent.name(), "CNY");
    UnitListResponse listResponse = new UnitListResponse(new UnitDTO[] {unitDTO}, 1, 0, 20);
    buildMockResource(
        Method.GET,
        unitPath,
        ImmutableMap.of("offset", "0", "limit", "20"),
        null,
        listResponse,
        SC_OK);
    PagedResult<Unit> units = catalog.asDatasetCatalog().listUnits(unitIdent.namespace(), 0, 20);
    Assertions.assertEquals(1, units.total());
    Assertions.assertEquals(unitIdent.name(), units.items().get(0).code());
    Assertions.assertInstanceOf(GenericUnit.class, units.items().get(0));

    buildMockResource(
        Method.GET, unitPath + "/" + unitIdent.name(), null, new UnitResponse(unitDTO), SC_OK);
    Unit loaded = catalog.asDatasetCatalog().getUnit(unitIdent);
    Assertions.assertEquals(unitIdent.name(), loaded.code());
    Assertions.assertInstanceOf(GenericUnit.class, loaded);

    UnitCreateRequest createRequest =
        new UnitCreateRequest(unitIdent.name(), "Yuan", "CNY", "comment");
    buildMockResource(Method.POST, unitPath, createRequest, new UnitResponse(unitDTO), SC_OK);
    Unit created =
        catalog
            .asDatasetCatalog()
            .createUnit(unitIdent, unitIdent.name(), "Yuan", "CNY", "comment");
    Assertions.assertEquals(unitIdent.name(), created.code());
    Assertions.assertInstanceOf(GenericUnit.class, created);

    UnitUpdateRequest updateRequest = new UnitUpdateRequest("Yuan New", "RMB", "updated");
    buildMockResource(
        Method.PUT,
        unitPath + "/" + unitIdent.name(),
        updateRequest,
        new UnitResponse(mockUnitDTO(unitIdent.name(), "RMB")),
        SC_OK);
    Unit altered = catalog.asDatasetCatalog().alterUnit(unitIdent, "Yuan New", "RMB", "updated");
    Assertions.assertEquals("RMB", altered.symbol());
    Assertions.assertInstanceOf(GenericUnit.class, altered);

    buildMockResource(
        Method.DELETE, unitPath + "/" + unitIdent.name(), null, new DropResponse(true), SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteUnit(unitIdent));

    ErrorResponse noSuchUnit =
        ErrorResponse.notFound(NoSuchUnitException.class.getSimpleName(), "unit not found");
    buildMockResource(
        Method.GET, unitPath + "/" + unitIdent.name(), null, noSuchUnit, SC_NOT_FOUND);
    Assertions.assertThrows(
        NoSuchUnitException.class, () -> catalog.asDatasetCatalog().getUnit(unitIdent));

    ErrorResponse alreadyExists =
        ErrorResponse.alreadyExists(
            UnitAlreadyExistsException.class.getSimpleName(), "unit exists");
    buildMockResource(Method.POST, unitPath, createRequest, alreadyExists, SC_CONFLICT);
    Assertions.assertThrows(
        UnitAlreadyExistsException.class,
        () ->
            catalog
                .asDatasetCatalog()
                .createUnit(unitIdent, unitIdent.name(), "Yuan", "CNY", "comment"));
  }

  @Test
  public void testValueDomainOperations() throws JsonProcessingException {
    NameIdentifier domainIdent = NameIdentifier.of("schema1", "country_domain");
    Namespace namespace = Namespace.of(METALAKE_NAME, CATALOG_NAME, "schema1");
    String domainPath = withSlash(GenericDatasetCatalog.formatValueDomainRequestPath(namespace));

    ValueDomainDTO domainDTO = mockValueDomainDTO(domainIdent.name());
    ValueDomainListResponse listResponse =
        new ValueDomainListResponse(new ValueDomainDTO[] {domainDTO}, 1, 0, 20);
    buildMockResource(
        Method.GET,
        domainPath,
        ImmutableMap.of("offset", "0", "limit", "20"),
        null,
        listResponse,
        SC_OK);
    PagedResult<ValueDomain> domains =
        catalog.asDatasetCatalog().listValueDomains(domainIdent.namespace(), 0, 20);
    Assertions.assertEquals(1, domains.total());
    Assertions.assertEquals(domainIdent.name(), domains.items().get(0).domainCode());
    Assertions.assertInstanceOf(GenericValueDomain.class, domains.items().get(0));

    buildMockResource(
        Method.GET,
        domainPath + "/" + domainIdent.name(),
        null,
        new ValueDomainResponse(domainDTO),
        SC_OK);
    ValueDomain loaded = catalog.asDatasetCatalog().getValueDomain(domainIdent);
    Assertions.assertEquals(domainIdent.name(), loaded.domainCode());
    Assertions.assertInstanceOf(GenericValueDomain.class, loaded);

    List<ValueDomain.Item> items =
        ImmutableList.of(
            new ValueDomainItemDTO("CN", "China"), new ValueDomainItemDTO("US", "USA"));
    ValueDomainCreateRequest createRequest =
        new ValueDomainCreateRequest(
            domainIdent.name(),
            "Country Domain",
            ValueDomain.Type.ENUM,
            ValueDomain.Level.BUSINESS,
            ImmutableList.of(
                new ValueDomainItemDTO("CN", "China"), new ValueDomainItemDTO("US", "USA")),
            "comment",
            "STRING");
    buildMockResource(
        Method.POST, domainPath, createRequest, new ValueDomainResponse(domainDTO), SC_OK);
    ValueDomain created =
        catalog
            .asDatasetCatalog()
            .createValueDomain(
                domainIdent,
                domainIdent.name(),
                "Country Domain",
                ValueDomain.Type.ENUM,
                ValueDomain.Level.BUSINESS,
                items,
                "comment",
                "STRING");
    Assertions.assertEquals(domainIdent.name(), created.domainCode());
    Assertions.assertInstanceOf(GenericValueDomain.class, created);

    ValueDomainUpdateRequest updateRequest =
        new ValueDomainUpdateRequest(
            "Country Domain New",
            ValueDomain.Level.BUSINESS,
            ImmutableList.of(new ValueDomainItemDTO("JP", "Japan")),
            "updated",
            "STRING");
    buildMockResource(
        Method.PUT,
        domainPath + "/" + domainIdent.name(),
        updateRequest,
        new ValueDomainResponse(mockValueDomainDTO(domainIdent.name())),
        SC_OK);
    ValueDomain altered =
        catalog
            .asDatasetCatalog()
            .alterValueDomain(
                domainIdent,
                "Country Domain New",
                ValueDomain.Level.BUSINESS,
                ImmutableList.of(new ValueDomainItemDTO("JP", "Japan")),
                "updated",
                "STRING");
    Assertions.assertEquals(domainIdent.name(), altered.domainCode());
    Assertions.assertInstanceOf(GenericValueDomain.class, altered);

    buildMockResource(
        Method.DELETE, domainPath + "/" + domainIdent.name(), null, new DropResponse(true), SC_OK);
    Assertions.assertTrue(catalog.asDatasetCatalog().deleteValueDomain(domainIdent));

    ErrorResponse noSuchValueDomain =
        ErrorResponse.notFound(
            NoSuchValueDomainException.class.getSimpleName(), "value domain not found");
    buildMockResource(
        Method.GET, domainPath + "/" + domainIdent.name(), null, noSuchValueDomain, SC_NOT_FOUND);
    Assertions.assertThrows(
        NoSuchValueDomainException.class,
        () -> catalog.asDatasetCatalog().getValueDomain(domainIdent));

    ErrorResponse alreadyExists =
        ErrorResponse.alreadyExists(
            ValueDomainAlreadyExistsException.class.getSimpleName(), "value domain exists");
    buildMockResource(Method.POST, domainPath, createRequest, alreadyExists, SC_CONFLICT);
    Assertions.assertThrows(
        ValueDomainAlreadyExistsException.class,
        () ->
            catalog
                .asDatasetCatalog()
                .createValueDomain(
                    domainIdent,
                    domainIdent.name(),
                    "Country Domain",
                    ValueDomain.Type.ENUM,
                    ValueDomain.Level.BUSINESS,
                    items,
                    "comment",
                    "STRING"));

    ErrorResponse internalError = ErrorResponse.internalError("internal error");
    buildMockResource(
        Method.GET,
        domainPath + "/" + domainIdent.name(),
        null,
        internalError,
        SC_INTERNAL_SERVER_ERROR);
    Assertions.assertThrows(
        RuntimeException.class, () -> catalog.asDatasetCatalog().getValueDomain(domainIdent));
  }

  private static MetricDTO mockMetricDTO(String name, String code) {
    return MetricDTO.builder()
        .withName(name)
        .withCode(code)
        .withType(Metric.Type.ATOMIC)
        .withDataType("DECIMAL(18,2)")
        .withUnit("CNY")
        .withUnitName("Yuan")
        .withComment("comment")
        .withProperties(ImmutableMap.of("k1", "v1"))
        .withCurrentVersion(1)
        .withLastVersion(1)
        .withAudit(audit())
        .build();
  }

  private static MetricVersionDTO mockMetricVersionDTO(int version, String name, String code) {
    return MetricVersionDTO.builder()
        .withId((long) version)
        .withVersion(version)
        .withName(name)
        .withCode(code)
        .withType(Metric.Type.ATOMIC)
        .withDataType("DECIMAL(18,2)")
        .withComment("comment")
        .withUnit("CNY")
        .withUnitName("Yuan")
        .withCalculationFormula("SUM(price)")
        .withRefTableId(1L)
        .withMeasureColumnIds("[1]")
        .withFilterColumnIds("[2]")
        .withAudit(audit())
        .build();
  }

  private static MetricModifierDTO mockMetricModifierDTO(String name, String code) {
    return MetricModifierDTO.builder()
        .withName(name)
        .withCode(code)
        .withComment("comment")
        .withModifierType("ENUM")
        .withAudit(audit())
        .build();
  }

  private static WordRootDTO mockWordRootDTO(String code, String name) {
    return WordRootDTO.builder()
        .withCode(code)
        .withName(name)
        .withDataType("STRING")
        .withComment("comment")
        .withAudit(audit())
        .build();
  }

  private static UnitDTO mockUnitDTO(String code, String symbol) {
    return UnitDTO.builder()
        .withCode(code)
        .withName("Unit Name")
        .withSymbol(symbol)
        .withComment("comment")
        .withAudit(audit())
        .build();
  }

  private static ValueDomainDTO mockValueDomainDTO(String domainCode) {
    return ValueDomainDTO.builder()
        .withDomainCode(domainCode)
        .withDomainName("Country Domain")
        .withDomainType(ValueDomain.Type.ENUM)
        .withDomainLevel(ValueDomain.Level.BUSINESS)
        .withItems(ImmutableList.of(new ValueDomainItemDTO("CN", "China")))
        .withComment("comment")
        .withDataType("STRING")
        .withAudit(audit())
        .build();
  }

  private static AuditDTO audit() {
    return AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build();
  }
}
