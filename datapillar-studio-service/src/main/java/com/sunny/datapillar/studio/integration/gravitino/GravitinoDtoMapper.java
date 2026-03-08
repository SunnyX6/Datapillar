package com.sunny.datapillar.studio.integration.gravitino;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoAuditResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoCatalogSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoGroupResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoGroupSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoMetricVersionSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoModifierSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoPageResult;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRoleResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRoleSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoSchemaSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableColumnResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTableSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoTagSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUnitSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoUserResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoValueDomainSummaryResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootResponse;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoWordRootSummaryResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.gravitino.Audit;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.Schema;
import org.apache.gravitino.authorization.Group;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Role;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.authorization.User;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.rel.Column;
import org.apache.gravitino.rel.Table;
import org.apache.gravitino.tag.Tag;

public final class GravitinoDtoMapper {

  private GravitinoDtoMapper() {}

  public static GravitinoAuditResponse mapAudit(Audit audit) {
    if (audit == null) {
      return null;
    }
    GravitinoAuditResponse response = new GravitinoAuditResponse();
    response.setCreator(audit.creator());
    response.setCreateTime(audit.createTime());
    response.setLastModifier(audit.lastModifier());
    response.setLastModifiedTime(audit.lastModifiedTime());
    return response;
  }

  public static GravitinoOwnerResponse mapOwner(Optional<Owner> ownerOptional) {
    if (ownerOptional == null || ownerOptional.isEmpty()) {
      return null;
    }
    return mapOwner(ownerOptional.get());
  }

  public static GravitinoOwnerResponse mapOwner(Owner owner) {
    if (owner == null) {
      return null;
    }
    GravitinoOwnerResponse response = new GravitinoOwnerResponse();
    response.setName(owner.name());
    response.setType(owner.type().name());
    return response;
  }

  public static <S, T> GravitinoPageResult<T> mapPage(
      PagedResult<S> page, Function<S, T> itemMapper) {
    GravitinoPageResult<T> response = new GravitinoPageResult<>();
    if (page == null) {
      response.setItems(List.of());
      response.setTotal(0L);
      response.setOffset(0);
      response.setLimit(0);
      return response;
    }
    List<T> items = new ArrayList<>();
    if (page.items() != null) {
      for (S item : page.items()) {
        items.add(itemMapper.apply(item));
      }
    }
    response.setItems(items);
    response.setTotal(page.total());
    response.setOffset(page.offset());
    response.setLimit(page.limit());
    return response;
  }

  public static List<GravitinoRolePrivilegeItemResponse> mapRolePrivileges(
      String metalake, Role role) {
    List<GravitinoRolePrivilegeItemResponse> items = new ArrayList<>();
    if (role == null || role.securableObjects() == null) {
      return items;
    }
    for (SecurableObject securableObject : role.securableObjects()) {
      for (Privilege privilege : securableObject.privileges()) {
        GravitinoRolePrivilegeItemResponse item = new GravitinoRolePrivilegeItemResponse();
        item.setMetalake(metalake);
        item.setObjectType(securableObject.type().name());
        item.setObjectName(securableObject.fullName());
        if (securableObject.type() == MetadataObject.Type.COLUMN) {
          String[] parts = securableObject.fullName().split("\\.");
          if (parts.length >= 4) {
            item.setColumnName(parts[parts.length - 1]);
            item.setObjectName(String.join(".", Arrays.copyOf(parts, parts.length - 1)));
          }
        }
        item.setPrivilegeCode(privilege.name().name());
        items.add(item);
      }
    }
    return items;
  }

  public static GravitinoRoleSummaryResponse mapRoleSummary(String metalake, String roleName) {
    GravitinoRoleSummaryResponse response = new GravitinoRoleSummaryResponse();
    response.setMetalake(metalake);
    response.setName(roleName);
    return response;
  }

  public static GravitinoRoleResponse mapRole(String metalake, Role role) {
    if (role == null) {
      return null;
    }
    GravitinoRoleResponse response = new GravitinoRoleResponse();
    response.setMetalake(metalake);
    response.setName(role.name());
    response.setProperties(role.properties());
    response.setAudit(mapAudit(role.auditInfo()));
    return response;
  }

  public static GravitinoUserResponse mapUser(String metalake, User user) {
    if (user == null) {
      return null;
    }
    GravitinoUserResponse response = new GravitinoUserResponse();
    response.setMetalake(metalake);
    response.setName(user.name());
    response.setRoles(user.roles() == null ? List.of() : user.roles().stream().sorted().toList());
    response.setAudit(mapAudit(user.auditInfo()));
    return response;
  }

  public static GravitinoGroupSummaryResponse mapGroupSummary(String metalake, String groupName) {
    GravitinoGroupSummaryResponse response = new GravitinoGroupSummaryResponse();
    response.setMetalake(metalake);
    response.setName(groupName);
    return response;
  }

  public static GravitinoGroupResponse mapGroup(String metalake, Group group) {
    if (group == null) {
      return null;
    }
    GravitinoGroupResponse response = new GravitinoGroupResponse();
    response.setMetalake(metalake);
    response.setName(group.name());
    response.setRoles(group.roles() == null ? List.of() : group.roles().stream().sorted().toList());
    response.setAudit(mapAudit(group.auditInfo()));
    return response;
  }

  public static GravitinoCatalogSummaryResponse mapCatalogSummary(
      String metalake, Catalog catalog) {
    GravitinoCatalogSummaryResponse response = new GravitinoCatalogSummaryResponse();
    response.setMetalake(metalake);
    response.setName(catalog.name());
    response.setType(catalog.type().name());
    response.setProvider(catalog.provider());
    return response;
  }

  public static GravitinoCatalogResponse mapCatalog(
      String metalake, Catalog catalog, GravitinoOwnerResponse owner) {
    if (catalog == null) {
      return null;
    }
    GravitinoCatalogResponse response = new GravitinoCatalogResponse();
    response.setMetalake(metalake);
    response.setName(catalog.name());
    response.setType(catalog.type().name());
    response.setProvider(catalog.provider());
    response.setComment(catalog.comment());
    response.setProperties(catalog.properties());
    response.setAudit(mapAudit(catalog.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoSchemaSummaryResponse mapSchemaSummary(
      String metalake, String catalogName, String schemaName) {
    GravitinoSchemaSummaryResponse response = new GravitinoSchemaSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setName(schemaName);
    return response;
  }

  public static GravitinoSchemaResponse mapSchema(
      String metalake, String catalogName, Schema schema, GravitinoOwnerResponse owner) {
    if (schema == null) {
      return null;
    }
    GravitinoSchemaResponse response = new GravitinoSchemaResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setName(schema.name());
    response.setComment(schema.comment());
    response.setProperties(schema.properties());
    response.setAudit(mapAudit(schema.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoTableSummaryResponse mapTableSummary(
      String metalake, String catalogName, String schemaName, String tableName) {
    GravitinoTableSummaryResponse response = new GravitinoTableSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setName(tableName);
    return response;
  }

  public static GravitinoTableResponse mapTable(
      String metalake,
      String catalogName,
      String schemaName,
      Table table,
      GravitinoOwnerResponse owner) {
    if (table == null) {
      return null;
    }
    GravitinoTableResponse response = new GravitinoTableResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setName(table.name());
    response.setComment(table.comment());
    response.setProperties(table.properties());
    response.setColumns(mapColumns(table.columns()));
    response.setPartitioning(toStringList(table.partitioning()));
    response.setSortOrders(toStringList(table.sortOrder()));
    response.setDistribution(table.distribution() == null ? null : table.distribution().toString());
    response.setIndexes(toStringList(table.index()));
    response.setAudit(mapAudit(table.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoTagSummaryResponse mapTagSummary(String metalake, String tagName) {
    GravitinoTagSummaryResponse response = new GravitinoTagSummaryResponse();
    response.setMetalake(metalake);
    response.setName(tagName);
    return response;
  }

  public static GravitinoTagResponse mapTag(String metalake, Tag tag) {
    if (tag == null) {
      return null;
    }
    GravitinoTagResponse response = new GravitinoTagResponse();
    response.setMetalake(metalake);
    response.setName(tag.name());
    response.setComment(tag.comment());
    response.setProperties(tag.properties());
    response.setAudit(mapAudit(tag.auditInfo()));
    return response;
  }

  public static GravitinoWordRootSummaryResponse mapWordRootSummary(
      String metalake, String catalogName, String schemaName, WordRoot root) {
    GravitinoWordRootSummaryResponse response = new GravitinoWordRootSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(root.code());
    response.setName(root.name());
    return response;
  }

  public static GravitinoWordRootResponse mapWordRoot(
      String metalake,
      String catalogName,
      String schemaName,
      WordRoot root,
      GravitinoOwnerResponse owner) {
    if (root == null) {
      return null;
    }
    GravitinoWordRootResponse response = new GravitinoWordRootResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(root.code());
    response.setName(root.name());
    response.setDataType(root.dataType());
    response.setComment(root.comment());
    response.setAudit(mapAudit(root.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoMetricSummaryResponse mapMetricSummary(
      String metalake, String catalogName, String schemaName, Metric metric) {
    GravitinoMetricSummaryResponse response = new GravitinoMetricSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(metric.code());
    response.setName(metric.name());
    response.setType(metric.type().name());
    response.setCurrentVersion(metric.currentVersion());
    response.setLastVersion(metric.lastVersion());
    return response;
  }

  public static GravitinoMetricResponse mapMetric(
      String metalake,
      String catalogName,
      String schemaName,
      Metric metric,
      GravitinoOwnerResponse owner) {
    if (metric == null) {
      return null;
    }
    GravitinoMetricResponse response = new GravitinoMetricResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(metric.code());
    response.setName(metric.name());
    response.setType(metric.type().name());
    response.setComment(metric.comment());
    response.setDataType(metric.dataType());
    response.setUnit(metric.unit());
    response.setUnitName(metric.unitName());
    response.setProperties(metric.properties());
    response.setCurrentVersion(metric.currentVersion());
    response.setLastVersion(metric.lastVersion());
    response.setAudit(mapAudit(metric.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoMetricVersionSummaryResponse mapMetricVersionSummary(
      String metalake, String catalogName, String schemaName, String metricCode, int version) {
    GravitinoMetricVersionSummaryResponse response = new GravitinoMetricVersionSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setMetricCode(metricCode);
    response.setVersion(version);
    return response;
  }

  public static GravitinoMetricVersionResponse mapMetricVersion(
      String metalake, String catalogName, String schemaName, MetricVersion version) {
    if (version == null) {
      return null;
    }
    GravitinoMetricVersionResponse response = new GravitinoMetricVersionResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setMetricCode(version.metricCode());
    response.setId(version.id());
    response.setVersion(version.version());
    response.setMetricName(version.metricName());
    response.setMetricType(version.metricType().name());
    response.setComment(version.comment());
    response.setDataType(version.dataType());
    response.setUnit(version.unit());
    response.setUnitName(version.unitName());
    response.setUnitSymbol(version.unitSymbol());
    response.setParentMetricCodes(toStringList(version.parentMetricCodes()));
    response.setCalculationFormula(version.calculationFormula());
    response.setRefTableId(version.refTableId());
    response.setRefCatalogName(version.refCatalogName());
    response.setRefSchemaName(version.refSchemaName());
    response.setRefTableName(version.refTableName());
    response.setMeasureColumnIds(version.measureColumnIds());
    response.setFilterColumnIds(version.filterColumnIds());
    response.setProperties(version.properties());
    response.setAudit(mapAudit(version.auditInfo()));
    return response;
  }

  public static GravitinoUnitSummaryResponse mapUnitSummary(
      String metalake, String catalogName, String schemaName, Unit unit) {
    GravitinoUnitSummaryResponse response = new GravitinoUnitSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(unit.code());
    response.setName(unit.name());
    response.setSymbol(unit.symbol());
    return response;
  }

  public static GravitinoUnitResponse mapUnit(
      String metalake,
      String catalogName,
      String schemaName,
      Unit unit,
      GravitinoOwnerResponse owner) {
    if (unit == null) {
      return null;
    }
    GravitinoUnitResponse response = new GravitinoUnitResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(unit.code());
    response.setName(unit.name());
    response.setSymbol(unit.symbol());
    response.setComment(unit.comment());
    response.setAudit(mapAudit(unit.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoModifierSummaryResponse mapModifierSummary(
      String metalake, String catalogName, String schemaName, MetricModifier modifier) {
    GravitinoModifierSummaryResponse response = new GravitinoModifierSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(modifier.code());
    response.setName(modifier.name());
    response.setModifierType(modifier.modifierType());
    return response;
  }

  public static GravitinoModifierResponse mapModifier(
      String metalake,
      String catalogName,
      String schemaName,
      MetricModifier modifier,
      GravitinoOwnerResponse owner) {
    if (modifier == null) {
      return null;
    }
    GravitinoModifierResponse response = new GravitinoModifierResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setCode(modifier.code());
    response.setName(modifier.name());
    response.setModifierType(modifier.modifierType());
    response.setComment(modifier.comment());
    response.setAudit(mapAudit(modifier.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  public static GravitinoValueDomainSummaryResponse mapValueDomainSummary(
      String metalake, String catalogName, String schemaName, ValueDomain valueDomain) {
    GravitinoValueDomainSummaryResponse response = new GravitinoValueDomainSummaryResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setDomainCode(valueDomain.domainCode());
    response.setDomainName(valueDomain.domainName());
    response.setDomainType(valueDomain.domainType().name());
    response.setDomainLevel(valueDomain.domainLevel().name());
    return response;
  }

  public static GravitinoValueDomainResponse mapValueDomain(
      String metalake,
      String catalogName,
      String schemaName,
      ValueDomain valueDomain,
      GravitinoOwnerResponse owner) {
    if (valueDomain == null) {
      return null;
    }
    GravitinoValueDomainResponse response = new GravitinoValueDomainResponse();
    response.setMetalake(metalake);
    response.setCatalogName(catalogName);
    response.setSchemaName(schemaName);
    response.setDomainCode(valueDomain.domainCode());
    response.setDomainName(valueDomain.domainName());
    response.setDomainType(valueDomain.domainType().name());
    response.setDomainLevel(valueDomain.domainLevel().name());
    response.setItems(mapValueDomainItems(valueDomain.items()));
    response.setComment(valueDomain.comment());
    response.setDataType(valueDomain.dataType());
    response.setAudit(mapAudit(valueDomain.auditInfo()));
    response.setOwner(owner);
    return response;
  }

  private static List<GravitinoTableColumnResponse> mapColumns(Column[] columns) {
    List<GravitinoTableColumnResponse> responses = new ArrayList<>();
    if (columns == null) {
      return responses;
    }
    for (Column column : columns) {
      GravitinoTableColumnResponse response = new GravitinoTableColumnResponse();
      response.setName(column.name());
      response.setDataType(column.dataType() == null ? null : column.dataType().simpleString());
      response.setComment(column.comment());
      response.setNullable(column.nullable());
      response.setAutoIncrement(column.autoIncrement());
      if (column.defaultValue() != null && column.defaultValue() != Column.DEFAULT_VALUE_NOT_SET) {
        response.setDefaultValue(column.defaultValue().toString());
      }
      responses.add(response);
    }
    return responses;
  }

  private static List<GravitinoValueDomainItemResponse> mapValueDomainItems(
      List<ValueDomain.Item> items) {
    List<GravitinoValueDomainItemResponse> responses = new ArrayList<>();
    if (items == null) {
      return responses;
    }
    for (ValueDomain.Item item : items) {
      GravitinoValueDomainItemResponse response = new GravitinoValueDomainItemResponse();
      response.setValue(item.value());
      response.setLabel(item.label());
      responses.add(response);
    }
    return responses;
  }

  private static List<String> toStringList(Object[] values) {
    List<String> items = new ArrayList<>();
    if (values == null) {
      return items;
    }
    for (Object value : values) {
      if (value != null) {
        items.add(value.toString());
      }
    }
    return items;
  }
}
