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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.DatasetCatalog;
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
import org.apache.gravitino.dto.responses.DropResponse;
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
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.exceptions.NoSuchUnitException;
import org.apache.gravitino.exceptions.NoSuchValueDomainException;
import org.apache.gravitino.exceptions.NoSuchWordRootException;
import org.apache.gravitino.exceptions.UnitAlreadyExistsException;
import org.apache.gravitino.exceptions.ValueDomainAlreadyExistsException;
import org.apache.gravitino.exceptions.WordRootAlreadyExistsException;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.rest.RESTUtils;

class GenericDatasetCatalog extends BaseSchemaCatalog implements DatasetCatalog {

  GenericDatasetCatalog(
      Namespace namespace,
      String catalogName,
      Catalog.Type catalogType,
      String provider,
      String comment,
      Map<String, String> properties,
      AuditDTO auditDTO,
      RESTClient restClient) {
    super(namespace, catalogName, catalogType, provider, comment, properties, auditDTO, restClient);
  }

  @Override
  public DatasetCatalog asDatasetCatalog() {
    return this;
  }

  @Override
  public PagedResult<Metric> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    checkDatasetNamespace(namespace, "Metric");
    checkPagination(offset, limit);

    Namespace fullNamespace = datasetFullNamespace(namespace);
    MetricListResponse response =
        restClient.get(
            formatMetricRequestPath(fullNamespace),
            ImmutableMap.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)),
            MetricListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();

    List<Metric> metrics =
        Arrays.stream(response.getMetrics())
            .map(metric -> wrapMetric(metric, fullNamespace))
            .collect(Collectors.toList());
    return new PagedResult<>(
        metrics, response.getTotal(), response.getOffset(), response.getLimit());
  }

  @Override
  public Metric getMetric(NameIdentifier ident) throws NoSuchMetricException {
    checkDatasetNameIdentifier(ident, "Metric");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricResponse response =
        restClient.get(
            formatMetricRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            MetricResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetric(response.getMetric(), fullNamespace);
  }

  @Override
  public Metric registerMetric(
      NameIdentifier ident,
      String name,
      String code,
      Metric.Type type,
      String dataType,
      String comment,
      Map<String, String> properties,
      String unit,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumnIds,
      String filterColumnIds)
      throws NoSuchSchemaException, MetricAlreadyExistsException {
    checkDatasetNameIdentifier(ident, "Metric");

    MetricRegisterRequest request =
        new MetricRegisterRequest(
            name,
            code,
            type,
            dataType,
            comment,
            properties,
            unit,
            parentMetricCodes,
            calculationFormula,
            refTableId,
            refCatalogName,
            refSchemaName,
            refTableName,
            measureColumnIds,
            filterColumnIds);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricResponse response =
        restClient.post(
            formatMetricRequestPath(fullNamespace),
            request,
            MetricResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetric(response.getMetric(), fullNamespace);
  }

  @Override
  public boolean deleteMetric(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Metric");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    DropResponse response =
        restClient.delete(
            formatMetricRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException {
    checkDatasetNameIdentifier(ident, "Metric");

    List<MetricUpdateRequest> updates =
        Arrays.stream(changes)
            .map(DTOConverters::toMetricUpdateRequest)
            .collect(Collectors.toList());
    MetricUpdatesRequest request = new MetricUpdatesRequest(updates);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricResponse response =
        restClient.put(
            formatMetricRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            request,
            MetricResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetric(response.getMetric(), fullNamespace);
  }

  @Override
  public int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException {
    checkDatasetNameIdentifier(ident, "Metric");

    NameIdentifier fullIdentifier = datasetFullNameIdentifier(ident);
    MetricVersionListResponse response =
        restClient.get(
            formatMetricVersionRequestPath(fullIdentifier) + "/versions",
            MetricVersionListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return response.getVersions();
  }

  @Override
  public MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException {
    checkDatasetNameIdentifier(ident, "Metric");

    int[] versions = listMetricVersions(ident);
    MetricVersion[] versionInfos = new MetricVersion[versions.length];
    for (int i = 0; i < versions.length; i++) {
      try {
        versionInfos[i] = getMetricVersion(ident, versions[i]);
      } catch (NoSuchMetricVersionException e) {
        throw new RuntimeException(
            String.format("Failed to load metric version [%d] for metric [%s]", versions[i], ident),
            e);
      }
    }
    return versionInfos;
  }

  @Override
  public MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException {
    checkDatasetNameIdentifier(ident, "Metric");
    Preconditions.checkArgument(version > 0, "Metric version must be greater than 0");

    NameIdentifier fullIdentifier = datasetFullNameIdentifier(ident);
    MetricVersionResponse response =
        restClient.get(
            formatMetricVersionRequestPath(fullIdentifier) + "/versions/" + version,
            MetricVersionResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricVersion(response.getVersion());
  }

  @Override
  public boolean deleteMetricVersion(NameIdentifier ident, int version) {
    checkDatasetNameIdentifier(ident, "Metric");
    Preconditions.checkArgument(version > 0, "Metric version must be greater than 0");

    NameIdentifier fullIdentifier = datasetFullNameIdentifier(ident);
    DropResponse response =
        restClient.delete(
            formatMetricVersionRequestPath(fullIdentifier) + "/versions/" + version,
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    checkDatasetNameIdentifier(ident, "Metric");
    Preconditions.checkArgument(targetVersion > 0, "Target metric version must be greater than 0");

    NameIdentifier fullIdentifier = datasetFullNameIdentifier(ident);
    MetricSwitchVersionRequest request = new MetricSwitchVersionRequest(targetVersion);
    request.validate();

    MetricVersionResponse response =
        restClient.put(
            formatMetricVersionRequestPath(fullIdentifier) + "/switch/versions/" + targetVersion,
            request,
            MetricVersionResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricVersion(response.getVersion());
  }

  @Override
  public MetricVersion alterMetricVersion(
      NameIdentifier ident,
      int version,
      String metricName,
      String metricCode,
      String metricType,
      String dataType,
      String comment,
      String unit,
      String unitName,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String measureColumnIds,
      String filterColumnIds)
      throws NoSuchMetricVersionException {
    checkDatasetNameIdentifier(ident, "Metric");
    Preconditions.checkArgument(version > 0, "Metric version must be greater than 0");

    MetricVersionUpdateRequest request =
        new MetricVersionUpdateRequest(
            metricName,
            metricCode,
            metricType,
            dataType,
            comment,
            unit,
            unitName,
            parentMetricCodes,
            calculationFormula,
            refTableId,
            measureColumnIds,
            filterColumnIds);
    request.validate();

    NameIdentifier fullIdentifier = datasetFullNameIdentifier(ident);
    MetricVersionResponse response =
        restClient.put(
            formatMetricVersionRequestPath(fullIdentifier) + "/versions/" + version,
            request,
            MetricVersionResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricVersion(response.getVersion());
  }

  @Override
  public PagedResult<MetricModifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    checkDatasetNamespace(namespace, "Metric modifier");
    checkPagination(offset, limit);

    Namespace fullNamespace = datasetFullNamespace(namespace);
    MetricModifierListResponse response =
        restClient.get(
            formatModifierRequestPath(fullNamespace),
            ImmutableMap.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)),
            MetricModifierListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();

    List<MetricModifier> modifiers =
        Arrays.stream(response.getModifiers())
            .map(modifier -> wrapMetricModifier(modifier, fullNamespace))
            .collect(Collectors.toList());
    return new PagedResult<>(
        modifiers, response.getTotal(), response.getOffset(), response.getLimit());
  }

  @Override
  public MetricModifier getMetricModifier(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Metric modifier");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricModifierResponse response =
        restClient.get(
            formatModifierRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            MetricModifierResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricModifier(response.getModifier(), fullNamespace);
  }

  @Override
  public MetricModifier createMetricModifier(
      NameIdentifier ident, String code, String comment, String modifierType)
      throws NoSuchSchemaException {
    checkDatasetNameIdentifier(ident, "Metric modifier");

    MetricModifierCreateRequest request =
        new MetricModifierCreateRequest(ident.name(), code, comment, modifierType);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricModifierResponse response =
        restClient.post(
            formatModifierRequestPath(fullNamespace),
            request,
            MetricModifierResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricModifier(response.getModifier(), fullNamespace);
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Metric modifier");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    DropResponse response =
        restClient.delete(
            formatModifierRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public MetricModifier alterMetricModifier(NameIdentifier ident, String name, String comment) {
    checkDatasetNameIdentifier(ident, "Metric modifier");

    MetricModifierUpdateRequest request = new MetricModifierUpdateRequest(name, comment);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    MetricModifierResponse response =
        restClient.put(
            formatModifierRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            request,
            MetricModifierResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.metricErrorHandler());
    response.validate();
    return wrapMetricModifier(response.getModifier(), fullNamespace);
  }

  @Override
  public PagedResult<WordRoot> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    checkDatasetNamespace(namespace, "Word root");
    checkPagination(offset, limit);

    Namespace fullNamespace = datasetFullNamespace(namespace);
    WordRootListResponse response =
        restClient.get(
            formatWordRootRequestPath(fullNamespace),
            ImmutableMap.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)),
            WordRootListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.wordRootErrorHandler());
    response.validate();

    List<WordRoot> roots =
        Arrays.stream(response.getRoots())
            .map(root -> wrapWordRoot(root, fullNamespace))
            .collect(Collectors.toList());
    return new PagedResult<>(roots, response.getTotal(), response.getOffset(), response.getLimit());
  }

  @Override
  public WordRoot getWordRoot(NameIdentifier ident) throws NoSuchWordRootException {
    checkDatasetNameIdentifier(ident, "Word root");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    WordRootResponse response =
        restClient.get(
            formatWordRootRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            WordRootResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.wordRootErrorHandler());
    response.validate();
    return wrapWordRoot(response.getRoot(), fullNamespace);
  }

  @Override
  public WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException, WordRootAlreadyExistsException {
    checkDatasetNameIdentifier(ident, "Word root");

    WordRootCreateRequest request = new WordRootCreateRequest(code, name, dataType, comment);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    WordRootResponse response =
        restClient.post(
            formatWordRootRequestPath(fullNamespace),
            request,
            WordRootResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.wordRootErrorHandler());
    response.validate();
    return wrapWordRoot(response.getRoot(), fullNamespace);
  }

  @Override
  public boolean deleteWordRoot(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Word root");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    DropResponse response =
        restClient.delete(
            formatWordRootRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.wordRootErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public WordRoot alterWordRoot(NameIdentifier ident, String name, String dataType, String comment)
      throws NoSuchWordRootException {
    checkDatasetNameIdentifier(ident, "Word root");

    WordRootUpdateRequest request = new WordRootUpdateRequest(name, dataType, comment);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    WordRootResponse response =
        restClient.put(
            formatWordRootRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            request,
            WordRootResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.wordRootErrorHandler());
    response.validate();
    return wrapWordRoot(response.getRoot(), fullNamespace);
  }

  @Override
  public PagedResult<Unit> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    checkDatasetNamespace(namespace, "Unit");
    checkPagination(offset, limit);

    Namespace fullNamespace = datasetFullNamespace(namespace);
    UnitListResponse response =
        restClient.get(
            formatUnitRequestPath(fullNamespace),
            ImmutableMap.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)),
            UnitListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.unitErrorHandler());
    response.validate();

    List<Unit> units =
        Arrays.stream(response.getUnits())
            .map(unit -> wrapUnit(unit, fullNamespace))
            .collect(Collectors.toList());
    return new PagedResult<>(units, response.getTotal(), response.getOffset(), response.getLimit());
  }

  @Override
  public Unit getUnit(NameIdentifier ident) throws NoSuchUnitException {
    checkDatasetNameIdentifier(ident, "Unit");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    UnitResponse response =
        restClient.get(
            formatUnitRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            UnitResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.unitErrorHandler());
    response.validate();
    return wrapUnit(response.getUnit(), fullNamespace);
  }

  @Override
  public Unit createUnit(
      NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException, UnitAlreadyExistsException {
    checkDatasetNameIdentifier(ident, "Unit");

    UnitCreateRequest request = new UnitCreateRequest(code, name, symbol, comment);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    UnitResponse response =
        restClient.post(
            formatUnitRequestPath(fullNamespace),
            request,
            UnitResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.unitErrorHandler());
    response.validate();
    return wrapUnit(response.getUnit(), fullNamespace);
  }

  @Override
  public boolean deleteUnit(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Unit");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    DropResponse response =
        restClient.delete(
            formatUnitRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.unitErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment)
      throws NoSuchUnitException {
    checkDatasetNameIdentifier(ident, "Unit");

    UnitUpdateRequest request = new UnitUpdateRequest(name, symbol, comment);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    UnitResponse response =
        restClient.put(
            formatUnitRequestPath(fullNamespace) + "/" + RESTUtils.encodeString(ident.name()),
            request,
            UnitResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.unitErrorHandler());
    response.validate();
    return wrapUnit(response.getUnit(), fullNamespace);
  }

  @Override
  public PagedResult<ValueDomain> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    checkDatasetNamespace(namespace, "Value domain");
    checkPagination(offset, limit);

    Namespace fullNamespace = datasetFullNamespace(namespace);
    ValueDomainListResponse response =
        restClient.get(
            formatValueDomainRequestPath(fullNamespace),
            ImmutableMap.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)),
            ValueDomainListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.valueDomainErrorHandler());
    response.validate();

    List<ValueDomain> valueDomains =
        Arrays.stream(response.getValueDomains())
            .map(valueDomain -> wrapValueDomain(valueDomain, fullNamespace))
            .collect(Collectors.toList());
    return new PagedResult<>(
        valueDomains, response.getTotal(), response.getOffset(), response.getLimit());
  }

  @Override
  public ValueDomain getValueDomain(NameIdentifier ident) throws NoSuchValueDomainException {
    checkDatasetNameIdentifier(ident, "Value domain");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    ValueDomainResponse response =
        restClient.get(
            formatValueDomainRequestPath(fullNamespace)
                + "/"
                + RESTUtils.encodeString(ident.name()),
            ValueDomainResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.valueDomainErrorHandler());
    response.validate();
    return wrapValueDomain(response.getValueDomain(), fullNamespace);
  }

  @Override
  public ValueDomain createValueDomain(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      ValueDomain.Type domainType,
      ValueDomain.Level domainLevel,
      List<ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchSchemaException, ValueDomainAlreadyExistsException {
    checkDatasetNameIdentifier(ident, "Value domain");

    ValueDomainCreateRequest request =
        new ValueDomainCreateRequest(
            domainCode,
            domainName,
            domainType,
            domainLevel,
            toValueDomainItems(items),
            comment,
            dataType);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    ValueDomainResponse response =
        restClient.post(
            formatValueDomainRequestPath(fullNamespace),
            request,
            ValueDomainResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.valueDomainErrorHandler());
    response.validate();
    return wrapValueDomain(response.getValueDomain(), fullNamespace);
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    checkDatasetNameIdentifier(ident, "Value domain");

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    DropResponse response =
        restClient.delete(
            formatValueDomainRequestPath(fullNamespace)
                + "/"
                + RESTUtils.encodeString(ident.name()),
            DropResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.valueDomainErrorHandler());
    response.validate();
    return response.dropped();
  }

  @Override
  public ValueDomain alterValueDomain(
      NameIdentifier ident,
      String domainName,
      ValueDomain.Level domainLevel,
      List<ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchValueDomainException {
    checkDatasetNameIdentifier(ident, "Value domain");

    ValueDomainUpdateRequest request =
        new ValueDomainUpdateRequest(
            domainName, domainLevel, toValueDomainItems(items), comment, dataType);
    request.validate();

    Namespace fullNamespace = datasetFullNamespace(ident.namespace());
    ValueDomainResponse response =
        restClient.put(
            formatValueDomainRequestPath(fullNamespace)
                + "/"
                + RESTUtils.encodeString(ident.name()),
            request,
            ValueDomainResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.valueDomainErrorHandler());
    response.validate();
    return wrapValueDomain(response.getValueDomain(), fullNamespace);
  }

  private Metric wrapMetric(MetricDTO metricDTO, Namespace metricNamespace) {
    return new GenericMetric(metricDTO, restClient, metricNamespace);
  }

  private MetricVersion wrapMetricVersion(MetricVersionDTO metricVersionDTO) {
    return new GenericMetricVersion(metricVersionDTO);
  }

  private MetricModifier wrapMetricModifier(
      MetricModifierDTO modifierDTO, Namespace modifierNamespace) {
    return new GenericMetricModifier(modifierDTO, restClient, modifierNamespace);
  }

  private WordRoot wrapWordRoot(WordRootDTO wordRootDTO, Namespace rootNamespace) {
    return new GenericWordRoot(wordRootDTO, restClient, rootNamespace);
  }

  private Unit wrapUnit(UnitDTO unitDTO, Namespace unitNamespace) {
    return new GenericUnit(unitDTO, restClient, unitNamespace);
  }

  private ValueDomain wrapValueDomain(
      ValueDomainDTO valueDomainDTO, Namespace valueDomainNamespace) {
    return new GenericValueDomain(valueDomainDTO, restClient, valueDomainNamespace);
  }

  public static Builder builder() {
    return new Builder();
  }

  @VisibleForTesting
  static String formatMetricRequestPath(Namespace namespace) {
    Namespace schemaNamespace = Namespace.of(namespace.level(0), namespace.level(1));
    return formatSchemaRequestPath(schemaNamespace)
        + "/"
        + RESTUtils.encodeString(namespace.level(2))
        + "/metrics";
  }

  @VisibleForTesting
  static String formatMetricVersionRequestPath(NameIdentifier metricIdent) {
    return formatMetricRequestPath(metricIdent.namespace())
        + "/"
        + RESTUtils.encodeString(metricIdent.name());
  }

  @VisibleForTesting
  static String formatModifierRequestPath(Namespace namespace) {
    return formatMetricRequestPath(namespace) + "/modifiers";
  }

  @VisibleForTesting
  static String formatWordRootRequestPath(Namespace namespace) {
    Namespace schemaNamespace = Namespace.of(namespace.level(0), namespace.level(1));
    return formatSchemaRequestPath(schemaNamespace)
        + "/"
        + RESTUtils.encodeString(namespace.level(2))
        + "/wordroots";
  }

  @VisibleForTesting
  static String formatUnitRequestPath(Namespace namespace) {
    Namespace schemaNamespace = Namespace.of(namespace.level(0), namespace.level(1));
    return formatSchemaRequestPath(schemaNamespace)
        + "/"
        + RESTUtils.encodeString(namespace.level(2))
        + "/units";
  }

  @VisibleForTesting
  static String formatValueDomainRequestPath(Namespace namespace) {
    Namespace schemaNamespace = Namespace.of(namespace.level(0), namespace.level(1));
    return formatSchemaRequestPath(schemaNamespace)
        + "/"
        + RESTUtils.encodeString(namespace.level(2))
        + "/valuedomains";
  }

  static void checkDatasetNamespace(Namespace namespace, String objectName) {
    Namespace.check(
        namespace != null && namespace.length() == 1,
        "%s namespace must be non-null and have 1 level, the input namespace is %s",
        objectName,
        namespace);
  }

  static void checkDatasetNameIdentifier(NameIdentifier ident, String objectName) {
    NameIdentifier.check(ident != null, "NameIdentifier of %s must not be null", objectName);
    NameIdentifier.check(
        StringUtils.isNotBlank(ident.name()),
        "NameIdentifier name of %s must not be empty",
        objectName);
    checkDatasetNamespace(ident.namespace(), objectName);
  }

  private static List<ValueDomainItemDTO> toValueDomainItems(List<ValueDomain.Item> items) {
    if (items == null) {
      return null;
    }
    return items.stream()
        .map(item -> new ValueDomainItemDTO(item.value(), item.label()))
        .collect(Collectors.toList());
  }

  private static void checkPagination(int offset, int limit) {
    Preconditions.checkArgument(offset >= 0, "offset must be non-negative");
    Preconditions.checkArgument(limit > 0, "limit must be positive");
  }

  private Namespace datasetFullNamespace(Namespace datasetNamespace) {
    return Namespace.of(catalogNamespace().level(0), name(), datasetNamespace.level(0));
  }

  private NameIdentifier datasetFullNameIdentifier(NameIdentifier ident) {
    return NameIdentifier.of(datasetFullNamespace(ident.namespace()), ident.name());
  }

  static class Builder extends CatalogDTO.Builder<Builder> {

    private RESTClient restClient;

    private Namespace namespace;

    private Builder() {}

    Builder withNamespace(Namespace namespace) {
      this.namespace = namespace;
      return this;
    }

    Builder withRestClient(RESTClient restClient) {
      this.restClient = restClient;
      return this;
    }

    @Override
    public GenericDatasetCatalog build() {
      Namespace.check(
          namespace != null && namespace.length() == 1,
          "Catalog namespace must be non-null and have 1 level, the input namespace is %s",
          namespace);
      Preconditions.checkArgument(StringUtils.isNotBlank(name), "name must not be blank");
      Preconditions.checkArgument(type != null, "type must not be null");
      Preconditions.checkArgument(StringUtils.isNotBlank(provider), "provider must not be blank");
      Preconditions.checkArgument(audit != null, "audit must not be null");
      Preconditions.checkArgument(restClient != null, "restClient must be set");

      return new GenericDatasetCatalog(
          namespace, name, type, provider, comment, properties, audit, restClient);
    }
  }
}
