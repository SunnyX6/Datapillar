package com.sunny.datapillar.studio.integration.gravitino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootSummaryResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.Schema;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.dataset.DatasetCatalog;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.dto.dataset.ValueDomainItemDTO;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.rel.Column;
import org.apache.gravitino.rel.Table;
import org.apache.gravitino.rel.TableCatalog;
import org.apache.gravitino.tag.SupportsTags;
import org.springframework.stereotype.Component;

@Component
public class GravitinoSemanticClient {

  private static final int DEFAULT_OFFSET = 0;
  private static final int DEFAULT_LIMIT = 20;

  private final GravitinoClientFactory clientFactory;
  private final ObjectMapper objectMapper;
  private final GravitinoExceptionMapper errorMapper;

  public GravitinoSemanticClient(
      GravitinoClientFactory clientFactory,
      ObjectMapper objectMapper,
      GravitinoExceptionMapper errorMapper) {
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
  }

  public GravitinoPageResult<GravitinoWordRootSummaryResponse> listWordRoots(
      String metalake, int offset, int limit) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      PagedResult<WordRoot> page =
          datasetCatalog(client)
              .listWordRoots(semanticNamespace(), sanitizeOffset(offset), sanitizeLimit(limit));
      return GravitinoDtoMapper.mapPage(
          page,
          root ->
              GravitinoDtoMapper.mapWordRootSummary(
                  managedMetalake, semanticCatalogName(), semanticSchemaName(), root));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoWordRootResponse createWordRoot(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String code = requiredNonBlank(normalizedBody, "code");
      WordRoot root =
          datasetCatalog(client)
              .createWordRoot(
                  semanticIdentifier(code),
                  code,
                  requiredNonBlank(normalizedBody, "name"),
                  nullableText(normalizedBody, "dataType"),
                  nullableTextAllowBlank(normalizedBody, "comment"));
      return GravitinoDtoMapper.mapWordRoot(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), root, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoWordRootResponse loadWordRoot(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      WordRoot root = datasetCatalog(client).getWordRoot(semanticIdentifier(normalizedCode));
      return GravitinoDtoMapper.mapWordRoot(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), root, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoWordRootResponse updateWordRoot(String metalake, String code, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    JsonNode normalizedBody = nullSafeBody(body);
    NameIdentifier ident = semanticIdentifier(normalizedCode);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      DatasetCatalog datasetCatalog = datasetCatalog(client);
      WordRoot current = datasetCatalog.getWordRoot(ident);
      WordRoot root =
          datasetCatalog.alterWordRoot(
              ident,
              mergedNonBlank(normalizedBody, "name", current.name()),
              mergedNullableText(normalizedBody, "dataType", current.dataType()),
              mergedNullableTextAllowBlank(normalizedBody, "comment", current.comment()));
      return GravitinoDtoMapper.mapWordRoot(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), root, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteWordRoot(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return datasetCatalog(client).deleteWordRoot(semanticIdentifier(requireName(code, "code")));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoPageResult<GravitinoMetricSummaryResponse> listMetrics(
      String metalake, int offset, int limit) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      PagedResult<Metric> page =
          datasetCatalog(client)
              .listMetrics(semanticNamespace(), sanitizeOffset(offset), sanitizeLimit(limit));
      return GravitinoDtoMapper.mapPage(
          page,
          metric ->
              GravitinoDtoMapper.mapMetricSummary(
                  managedMetalake, semanticCatalogName(), semanticSchemaName(), metric));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricResponse createMetric(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String code = requiredNonBlank(normalizedBody, "code");
      Metric metric =
          datasetCatalog(client)
              .registerMetric(
                  semanticIdentifier(code),
                  requiredNonBlank(normalizedBody, "name"),
                  code,
                  parseMetricType(requiredNonBlank(normalizedBody, "type")),
                  nullableText(normalizedBody, "dataType"),
                  nullableTextAllowBlank(normalizedBody, "comment"),
                  parseStringMap(normalizedBody.get("properties")),
                  nullableText(normalizedBody, "unit"),
                  nullableStringArray(normalizedBody, "parentMetricCodes", null),
                  nullableText(normalizedBody, "calculationFormula"),
                  nullableLong(normalizedBody, "refTableId", null),
                  nullableText(normalizedBody, "refCatalogName"),
                  nullableText(normalizedBody, "refSchemaName"),
                  nullableText(normalizedBody, "refTableName"),
                  nullableText(normalizedBody, "measureColumnIds"),
                  nullableText(normalizedBody, "filterColumnIds"));
      return GravitinoDtoMapper.mapMetric(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), metric, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricResponse loadMetric(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Metric metric = datasetCatalog(client).getMetric(semanticIdentifier(normalizedCode));
      return GravitinoDtoMapper.mapMetric(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), metric, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricResponse updateMetric(String metalake, String code, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Metric metric =
          datasetCatalog(client)
              .alterMetric(ident, parseMetricChanges(normalizedBody.path("updates")));
      return GravitinoDtoMapper.mapMetric(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), metric, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteMetric(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return datasetCatalog(client).deleteMetric(semanticIdentifier(requireName(code, "code")));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoMetricVersionSummaryResponse> listMetricVersions(
      String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      int[] versions =
          datasetCatalog(client).listMetricVersions(semanticIdentifier(normalizedCode));
      List<GravitinoMetricVersionSummaryResponse> items = new ArrayList<>();
      for (int version : versions) {
        items.add(
            GravitinoDtoMapper.mapMetricVersionSummary(
                managedMetalake,
                semanticCatalogName(),
                semanticSchemaName(),
                normalizedCode,
                version));
      }
      return items;
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricVersionResponse loadMetricVersion(
      String metalake, String code, int version) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    int normalizedVersion = parseVersion(String.valueOf(version));
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return GravitinoDtoMapper.mapMetricVersion(
          managedMetalake,
          semanticCatalogName(),
          semanticSchemaName(),
          datasetCatalog(client).getMetricVersion(ident, normalizedVersion));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricVersionResponse updateMetricVersion(
      String metalake, String code, int version, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    int normalizedVersion = parseVersion(String.valueOf(version));
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      DatasetCatalog datasetCatalog = datasetCatalog(client);
      MetricVersion current = datasetCatalog.getMetricVersion(ident, normalizedVersion);
      MetricVersion updated =
          datasetCatalog.alterMetricVersion(
              ident,
              normalizedVersion,
              mergedNonBlank(normalizedBody, "metricName", current.metricName()),
              mergedNonBlank(normalizedBody, "metricCode", current.metricCode()),
              mergedNonBlank(normalizedBody, "metricType", current.metricType().name()),
              mergedNullableText(normalizedBody, "dataType", current.dataType()),
              mergedNullableTextAllowBlank(normalizedBody, "comment", current.comment()),
              mergedNullableText(normalizedBody, "unit", current.unit()),
              mergedNullableText(normalizedBody, "unitName", current.unitName()),
              nullableStringArray(normalizedBody, "parentMetricCodes", current.parentMetricCodes()),
              mergedNullableText(
                  normalizedBody, "calculationFormula", current.calculationFormula()),
              nullableLong(normalizedBody, "refTableId", current.refTableId()),
              mergedNullableText(normalizedBody, "measureColumnIds", current.measureColumnIds()),
              mergedNullableText(normalizedBody, "filterColumnIds", current.filterColumnIds()));
      return GravitinoDtoMapper.mapMetricVersion(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), updated);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoMetricVersionResponse switchMetricVersion(
      String metalake, String code, int version) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    int normalizedVersion = parseVersion(String.valueOf(version));
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return GravitinoDtoMapper.mapMetricVersion(
          managedMetalake,
          semanticCatalogName(),
          semanticSchemaName(),
          datasetCatalog(client).switchMetricVersion(ident, normalizedVersion));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoPageResult<GravitinoUnitSummaryResponse> listUnits(
      String metalake, int offset, int limit) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      PagedResult<Unit> page =
          datasetCatalog(client)
              .listUnits(semanticNamespace(), sanitizeOffset(offset), sanitizeLimit(limit));
      return GravitinoDtoMapper.mapPage(
          page,
          unit ->
              GravitinoDtoMapper.mapUnitSummary(
                  managedMetalake, semanticCatalogName(), semanticSchemaName(), unit));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoUnitResponse createUnit(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String code = requiredNonBlank(normalizedBody, "code");
      Unit unit =
          datasetCatalog(client)
              .createUnit(
                  semanticIdentifier(code),
                  code,
                  requiredNonBlank(normalizedBody, "name"),
                  nullableText(normalizedBody, "symbol"),
                  nullableTextAllowBlank(normalizedBody, "comment"));
      return GravitinoDtoMapper.mapUnit(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), unit, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoUnitResponse loadUnit(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      Unit unit = datasetCatalog(client).getUnit(semanticIdentifier(normalizedCode));
      return GravitinoDtoMapper.mapUnit(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), unit, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoUnitResponse updateUnit(String metalake, String code, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      DatasetCatalog datasetCatalog = datasetCatalog(client);
      Unit current = datasetCatalog.getUnit(ident);
      Unit unit =
          datasetCatalog.alterUnit(
              ident,
              mergedNonBlank(normalizedBody, "name", current.name()),
              mergedNullableText(normalizedBody, "symbol", current.symbol()),
              mergedNullableTextAllowBlank(normalizedBody, "comment", current.comment()));
      return GravitinoDtoMapper.mapUnit(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), unit, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteUnit(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return datasetCatalog(client).deleteUnit(semanticIdentifier(requireName(code, "code")));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoPageResult<GravitinoModifierSummaryResponse> listModifiers(
      String metalake, int offset, int limit) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      PagedResult<MetricModifier> page =
          datasetCatalog(client)
              .listMetricModifiers(
                  semanticNamespace(), sanitizeOffset(offset), sanitizeLimit(limit));
      return GravitinoDtoMapper.mapPage(
          page,
          modifier ->
              GravitinoDtoMapper.mapModifierSummary(
                  managedMetalake, semanticCatalogName(), semanticSchemaName(), modifier));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoModifierResponse createModifier(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String code = requiredNonBlank(normalizedBody, "code");
      MetricModifier modifier =
          datasetCatalog(client)
              .createMetricModifier(
                  semanticIdentifier(code),
                  code,
                  nullableTextAllowBlank(normalizedBody, "comment"),
                  nullableText(normalizedBody, "modifierType"));
      return GravitinoDtoMapper.mapModifier(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), modifier, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoModifierResponse loadModifier(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      MetricModifier modifier =
          datasetCatalog(client).getMetricModifier(semanticIdentifier(normalizedCode));
      return GravitinoDtoMapper.mapModifier(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), modifier, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoModifierResponse updateModifier(String metalake, String code, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      DatasetCatalog datasetCatalog = datasetCatalog(client);
      MetricModifier current = datasetCatalog.getMetricModifier(ident);
      MetricModifier modifier =
          datasetCatalog.alterMetricModifier(
              ident,
              mergedNonBlank(normalizedBody, "name", current.name()),
              mergedNullableTextAllowBlank(normalizedBody, "comment", current.comment()));
      return GravitinoDtoMapper.mapModifier(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), modifier, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteModifier(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return datasetCatalog(client)
          .deleteMetricModifier(semanticIdentifier(requireName(code, "code")));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoPageResult<GravitinoValueDomainSummaryResponse> listValueDomains(
      String metalake, int offset, int limit) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      PagedResult<ValueDomain> page =
          datasetCatalog(client)
              .listValueDomains(semanticNamespace(), sanitizeOffset(offset), sanitizeLimit(limit));
      return GravitinoDtoMapper.mapPage(
          page,
          domain ->
              GravitinoDtoMapper.mapValueDomainSummary(
                  managedMetalake, semanticCatalogName(), semanticSchemaName(), domain));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoValueDomainResponse createValueDomain(String metalake, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String domainCode = requiredNonBlank(normalizedBody, "domainCode");
      ValueDomain valueDomain =
          datasetCatalog(client)
              .createValueDomain(
                  semanticIdentifier(domainCode),
                  domainCode,
                  requiredNonBlank(normalizedBody, "domainName"),
                  parseValueDomainType(requiredNonBlank(normalizedBody, "domainType")),
                  parseValueDomainLevel(
                      nullableText(normalizedBody, "domainLevel"), ValueDomain.Level.BUSINESS),
                  parseValueDomainItems(normalizedBody.get("items")),
                  nullableTextAllowBlank(normalizedBody, "comment"),
                  nullableText(normalizedBody, "dataType"));
      return GravitinoDtoMapper.mapValueDomain(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), valueDomain, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoValueDomainResponse loadValueDomain(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    String normalizedCode = requireName(code, "code");
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      ValueDomain valueDomain =
          datasetCatalog(client).getValueDomain(semanticIdentifier(normalizedCode));
      return GravitinoDtoMapper.mapValueDomain(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), valueDomain, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public GravitinoValueDomainResponse updateValueDomain(
      String metalake, String code, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    NameIdentifier ident = semanticIdentifier(requireName(code, "code"));
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      DatasetCatalog datasetCatalog = datasetCatalog(client);
      ValueDomain current = datasetCatalog.getValueDomain(ident);
      ValueDomain valueDomain =
          datasetCatalog.alterValueDomain(
              ident,
              mergedNonBlank(normalizedBody, "domainName", current.domainName()),
              parseValueDomainLevel(
                  nullableText(normalizedBody, "domainLevel"), current.domainLevel()),
              normalizedBody.has("items")
                  ? parseValueDomainItems(normalizedBody.get("items"))
                  : current.items() == null ? Collections.emptyList() : current.items(),
              mergedNullableTextAllowBlank(normalizedBody, "comment", current.comment()),
              mergedNullableText(normalizedBody, "dataType", current.dataType()));
      return GravitinoDtoMapper.mapValueDomain(
          managedMetalake, semanticCatalogName(), semanticSchemaName(), valueDomain, null);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public boolean deleteValueDomain(String metalake, String code) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return datasetCatalog(client)
          .deleteValueDomain(semanticIdentifier(requireName(code, "code")));
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTagSummaryResponse> listObjectTags(
      String metalake, String objectType, String fullName) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      return mapTagNames(
          managedMetalake, resolveTagTarget(client, objectType, fullName).listTags());
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  public List<GravitinoTagSummaryResponse> alterObjectTags(
      String metalake, String objectType, String fullName, JsonNode body) {
    String managedMetalake = clientFactory.requireManagedMetalake(metalake);
    JsonNode normalizedBody = nullSafeBody(body);
    try (GravitinoClient client = clientFactory.createClient(managedMetalake, null)) {
      String[] tagNames =
          resolveTagTarget(client, objectType, fullName)
              .associateTags(
                  arrayOrEmpty(nullableStringArray(normalizedBody, "tagsToAdd", new String[0])),
                  arrayOrEmpty(nullableStringArray(normalizedBody, "tagsToRemove", new String[0])));
      return mapTagNames(managedMetalake, tagNames);
    } catch (RuntimeException exception) {
      throw errorMapper.map(exception);
    }
  }

  private List<GravitinoTagSummaryResponse> mapTagNames(String metalake, String[] tagNames) {
    if (tagNames == null || tagNames.length == 0) {
      return List.of();
    }
    return Arrays.stream(tagNames)
        .sorted()
        .map(tagName -> GravitinoDtoMapper.mapTagSummary(metalake, tagName))
        .toList();
  }

  private SupportsTags resolveTagTarget(
      GravitinoClient client, String objectType, String fullName) {
    String normalizedType = objectType == null ? "" : objectType.trim().toUpperCase(Locale.ROOT);
    String[] parts = splitFullName(fullName);
    return switch (normalizedType) {
      case "CATALOG" -> {
        if (parts.length != 1) {
          throw badRequest("Catalog fullName must be catalogName");
        }
        yield loadCatalog(client, parts[0]).supportsTags();
      }
      case "SCHEMA" -> {
        if (parts.length < 2) {
          throw badRequest("Schema fullName must be catalog.schema");
        }
        Catalog catalog = loadCatalog(client, parts[0]);
        String schemaName = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
        Schema schema = catalog.asSchemas().loadSchema(schemaName);
        yield schema.supportsTags();
      }
      case "TABLE" -> loadTable(client, parts).supportsTags();
      case "COLUMN" -> {
        if (parts.length < 4) {
          throw badRequest("Column fullName must be catalog.schema.table.column");
        }
        String[] tableParts = Arrays.copyOf(parts, parts.length - 1);
        yield resolveColumn(loadTable(client, tableParts), parts[parts.length - 1]).supportsTags();
      }
      default -> throw badRequest("Unsupported objectType for tag operations: " + objectType);
    };
  }

  private Table loadTable(GravitinoClient client, String[] fullNameParts) {
    if (fullNameParts.length < 3) {
      throw badRequest("Table fullName must be catalog.schema.table");
    }
    Catalog catalog = loadCatalog(client, fullNameParts[0]);
    TableCatalog tableCatalog = catalog.asTableCatalog();
    String[] schemaParts = Arrays.copyOfRange(fullNameParts, 1, fullNameParts.length - 1);
    String tableName = fullNameParts[fullNameParts.length - 1];
    return tableCatalog.loadTable(NameIdentifier.of(Namespace.of(schemaParts), tableName));
  }

  private Column resolveColumn(Table table, String columnName) {
    for (Column column : table.columns()) {
      if (column.name().equals(columnName)) {
        return column;
      }
    }
    throw new NotFoundException(
        "Column not found for semantic object tag operation: %s", columnName);
  }

  private Catalog loadCatalog(GravitinoClient client, String catalogName) {
    return client.loadCatalog(catalogName);
  }

  private DatasetCatalog datasetCatalog(GravitinoClient client) {
    Catalog catalog = client.loadCatalog(semanticCatalogName());
    if (catalog.type() != Catalog.Type.DATASET) {
      throw badRequest("Configured semantic catalog is not DATASET type: " + semanticCatalogName());
    }
    return catalog.asDatasetCatalog();
  }

  private NameIdentifier semanticIdentifier(String name) {
    return NameIdentifier.of(semanticNamespace(), name);
  }

  private Namespace semanticNamespace() {
    return Namespace.of(semanticSchemaName());
  }

  private String semanticCatalogName() {
    return clientFactory.requiredSemanticCatalog();
  }

  private String semanticSchemaName() {
    return clientFactory.requiredSemanticSchema();
  }

  private Metric.Type parseMetricType(String value) {
    try {
      return Metric.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw badRequest("Unsupported metric type: " + value);
    }
  }

  private ValueDomain.Type parseValueDomainType(String value) {
    try {
      return ValueDomain.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw badRequest("Unsupported value domain type: " + value);
    }
  }

  private ValueDomain.Level parseValueDomainLevel(String value, ValueDomain.Level defaultLevel) {
    if (value == null || value.isBlank()) {
      return defaultLevel;
    }
    try {
      return ValueDomain.Level.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw badRequest("Unsupported value domain level: " + value);
    }
  }

  private List<ValueDomain.Item> parseValueDomainItems(JsonNode itemsNode) {
    if (itemsNode == null || itemsNode.isNull()) {
      return Collections.emptyList();
    }
    if (!itemsNode.isArray()) {
      throw badRequest("items must be an array");
    }
    List<ValueDomain.Item> items = new ArrayList<>();
    for (JsonNode itemNode : itemsNode) {
      items.add(
          new ValueDomainItemDTO(
              requiredNonBlank(itemNode, "value"), nullableTextAllowBlank(itemNode, "label")));
    }
    return items;
  }

  private MetricChange[] parseMetricChanges(JsonNode updatesNode) {
    if (updatesNode == null || !updatesNode.isArray() || updatesNode.isEmpty()) {
      throw badRequest("updates must be a non-empty array");
    }
    List<MetricChange> changes = new ArrayList<>();
    for (JsonNode updateNode : updatesNode) {
      String type = requiredNonBlank(updateNode, "@type").toLowerCase(Locale.ROOT);
      switch (type) {
        case "rename", "renamemetric" ->
            changes.add(MetricChange.rename(requiredNonBlank(updateNode, "newName")));
        case "updatecomment" ->
            changes.add(MetricChange.updateComment(requiredFieldText(updateNode, "newComment")));
        case "updatedatatype" ->
            changes.add(MetricChange.updateDataType(requiredNonBlank(updateNode, "newDataType")));
        case "setproperty" ->
            changes.add(
                MetricChange.setProperty(
                    requiredNonBlank(updateNode, "property"),
                    requiredFieldText(updateNode, "value")));
        case "removeproperty" ->
            changes.add(MetricChange.removeProperty(requiredNonBlank(updateNode, "property")));
        default -> throw badRequest("Unsupported metric update type: " + type);
      }
    }
    return changes.toArray(new MetricChange[0]);
  }

  private String requiredFieldText(JsonNode node, String field) {
    JsonNode valueNode = node == null ? null : node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      throw badRequest("Missing required field: " + field);
    }
    return valueNode.asText();
  }

  private String requiredNonBlank(JsonNode node, String field) {
    String value = nullableText(node, field);
    if (value == null || value.isBlank()) {
      throw badRequest("Missing required field: " + field);
    }
    return value;
  }

  private String requireName(String value, String field) {
    if (value == null || value.isBlank()) {
      throw badRequest(field + " must not be blank");
    }
    return value.trim();
  }

  private String nullableText(JsonNode node, String field) {
    JsonNode valueNode = node == null ? null : node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    String value = valueNode.asText();
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String nullableTextAllowBlank(JsonNode node, String field) {
    JsonNode valueNode = node == null ? null : node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    return valueNode.asText();
  }

  private String mergedNullableText(JsonNode node, String field, String defaultValue) {
    if (node == null || !node.has(field)) {
      return defaultValue;
    }
    return nullableText(node, field);
  }

  private String mergedNullableTextAllowBlank(JsonNode node, String field, String defaultValue) {
    if (node == null || !node.has(field)) {
      return defaultValue;
    }
    return nullableTextAllowBlank(node, field);
  }

  private String mergedNonBlank(JsonNode node, String field, String defaultValue) {
    if (node == null || !node.has(field)) {
      return defaultValue;
    }
    return requiredNonBlank(node, field);
  }

  private Long nullableLong(JsonNode node, String field, Long defaultValue) {
    if (node == null || !node.has(field)) {
      return defaultValue;
    }
    JsonNode valueNode = node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    if (valueNode.isNumber()) {
      return valueNode.asLong();
    }
    String textValue = valueNode.asText();
    if (textValue == null || textValue.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(textValue.trim());
    } catch (NumberFormatException ex) {
      throw badRequest("Invalid long value for field: " + field);
    }
  }

  private String[] nullableStringArray(JsonNode node, String field, String[] defaultValue) {
    if (node == null || !node.has(field)) {
      return defaultValue;
    }
    JsonNode valueNode = node.get(field);
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    if (!valueNode.isArray()) {
      throw badRequest("Field must be an array: " + field);
    }
    ArrayNode arrayNode = (ArrayNode) valueNode;
    List<String> values = new ArrayList<>();
    for (JsonNode item : arrayNode) {
      if (item == null || item.isNull()) {
        continue;
      }
      String text = item.asText();
      if (text == null || text.isBlank()) {
        continue;
      }
      values.add(text.trim());
    }
    return values.toArray(new String[0]);
  }

  private Map<String, String> parseStringMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Collections.emptyMap();
    }
    if (!node.isObject()) {
      throw badRequest("properties must be an object");
    }
    return objectMapper.convertValue(
        node,
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
  }

  private String[] splitFullName(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      throw badRequest("fullName must not be blank");
    }
    String[] parts =
        Arrays.stream(fullName.trim().split("\\."))
            .filter(part -> part != null && !part.isBlank())
            .toArray(String[]::new);
    if (parts.length == 0) {
      throw badRequest("fullName must not be blank");
    }
    return parts;
  }

  private int sanitizeOffset(int offset) {
    if (offset < 0) {
      throw badRequest("offset must be non-negative");
    }
    return offset;
  }

  private int sanitizeLimit(int limit) {
    if (limit <= 0) {
      throw badRequest("limit must be greater than 0");
    }
    return limit;
  }

  private int parseVersion(String value) {
    try {
      int version = Integer.parseInt(value.trim());
      if (version <= 0) {
        throw badRequest("version must be greater than 0");
      }
      return version;
    } catch (NumberFormatException ex) {
      throw badRequest("Invalid integer value for version");
    }
  }

  private JsonNode nullSafeBody(JsonNode body) {
    return body == null || body.isNull() ? objectMapper.createObjectNode() : body;
  }

  private String[] arrayOrEmpty(String[] values) {
    return values == null ? new String[0] : values;
  }

  private BadRequestException badRequest(String message) {
    return new BadRequestException(message);
  }
}
