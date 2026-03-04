/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/
package org.apache.gravitino.dataset;

import java.util.Map;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.annotation.Evolving;
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

/**
 * DatasetCatalog The interface is defined in schema Manage dataset objects in(Metric,WordRoot
 * Wait)public API.if catalog Implement support for dataset objects,You should implement this
 * interface.
 */
@Evolving
public interface DatasetCatalog {

  // ============================= Metric management =============================

  /**
   * List in pages schema Metrics under namespace
   *
   * @param namespace schema namespace
   * @param offset offset
   * @param limit page size
   * @return Paginated results,Contains complete indicator data
   * @throws NoSuchSchemaException if schema does not exist
   */
  PagedResult<Metric> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * Pass {@link NameIdentifier} from catalog Get indicator metadata
   *
   * @param ident indicator identifier
   * @return Metric metadata
   * @throws NoSuchMetricException If the indicator does not exist
   */
  Metric getMetric(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * use {@link NameIdentifier} Check if the indicator exists
   *
   * @param ident indicator identifier
   * @return Returns if indicator exists true,Otherwise return false
   */
  default boolean metricExists(NameIdentifier ident) {
    try {
      getMetric(ident);
      return true;
    } catch (NoSuchMetricException e) {
      return false;
    }
  }

  /**
   * in catalog registration indicator
   *
   * @param ident Name identifier of the indicator
   * @param name Indicator name(Chinese name)
   * @param code Indicator coding
   * @param type Indicator type
   * @param dataType data type,Such as DECIMAL(18,2)
   * @param comment Indicator notes,Optional,can be null
   * @param properties Indicator properties,Optional,can be null or empty
   * @param unit Index unit,Optional,can be null
   * @param parentMetricCodes Parent indicator encoding array(for derivation/Composite
   *     indicator),Optional,can be null
   * @param calculationFormula Calculation formula(for composite indicators),Optional,can be null
   * @param refTableId quotedTable ID(For atomic indicators),Optional,can be null
   * @param refCatalogName quotedCatalogName(for event sending),Optional,can be null
   * @param refSchemaName quotedSchemaName(for event sending),Optional,can be null
   * @param refTableName quotedTableName(for event sending),Optional,can be null
   * @param measureColumnIds measure columnID JSONarray,Optional,can be null
   * @param filterColumnIds Filter columnsID JSONarray,Optional,can be null
   * @return Registered indicator object
   * @throws NoSuchSchemaException if schema does not exist
   * @throws MetricAlreadyExistsException If the indicator already exists
   */
  Metric registerMetric(
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
      throws NoSuchSchemaException, MetricAlreadyExistsException;

  /**
   * from catalog Delete indicator.If the indicator does not exist,Return false.Deleting a metric
   * will also delete all versions linked to this metric.*
   *
   * @param ident Name identifier of the indicator
   * @return Returns if indicator is deleted true,Returns if indicator does not exist false
   */
  boolean deleteMetric(NameIdentifier ident);

  /**
   * Yes catalog Indicator applications in {@link MetricChange change}
   *
   * @param ident The indicator to be modified {@link NameIdentifier} Example
   * @param changes to be applied to the indicator {@link MetricChange} Example
   * @return updated {@link Metric} Example
   * @throws NoSuchMetricException If the indicator does not exist
   * @throws IllegalArgumentException If the change is rejected by the implementation
   */
  Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException,
          IllegalArgumentException; // ============================= MetricVersion management

  // =============================

  /**
   * Pass {@link NameIdentifier} List all versions of registered indicators
   *
   * @param ident Name identifier of the indicator
   * @return Array of indicator version numbers
   * @throws NoSuchMetricException If the indicator does not exist
   */
  int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * Pass {@link NameIdentifier} List all versions of registered indicators and their information
   *
   * @param ident Name identifier of the indicator
   * @return Array of indicator version information
   * @throws NoSuchMetricException If the indicator does not exist
   */
  MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * Pass {@link NameIdentifier} and version number from catalog Get indicator version
   *
   * @param ident Name identifier of the indicator
   * @param version Indicator version number
   * @return indicator version object
   * @throws NoSuchMetricVersionException If the indicator version does not exist
   */
  MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException;

  /**
   * Pass {@link NameIdentifier} and version number to check whether the indicator version exists
   *
   * @param ident Name identifier of the indicator
   * @param version Indicator version number
   * @return Returns if indicator version exists true,Otherwise return false
   */
  default boolean metricVersionExists(NameIdentifier ident, int version) {
    try {
      getMetricVersion(ident, version);
      return true;
    } catch (NoSuchMetricVersionException e) {
      return false;
    }
  }

  /**
   * Pass {@link NameIdentifier} and version number delete indicator version
   *
   * @param ident Name identifier of the indicator
   * @param version Indicator version number
   * @return Returns if indicator version is deleted true,Returns if indicator version does not
   *     exist false
   */
  boolean deleteMetricVersion(NameIdentifier ident, int version);

  /**
   * Switch the current version of the indicator to the specified version
   *
   * @param ident Name identifier of the indicator
   * @param targetVersion Target version number
   * @return target version {@link MetricVersion} Details
   * @throws NoSuchMetricException If the indicator does not exist
   * @throws NoSuchMetricVersionException If the target version does not exist
   * @throws IllegalArgumentException If the target version number is invalid or equal to the
   *     current version
   */
  MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException;

  /**
   * Modify the information of the specified version(A new version will be automatically created)
   *
   * @param ident Name identifier of the indicator
   * @param version version number
   * @param metricName Indicator name
   * @param metricCode Indicator coding
   * @param metricType Indicator type
   * @param dataType data type
   * @param comment Release Notes
   * @param unit Index unit
   * @param unitName Indicator unit name
   * @param parentMetricCodes Parent indicator encoding array
   * @param calculationFormula Calculation formula
   * @param refTableId quotedTable ID
   * @param measureColumnIds measure columnID JSONarray
   * @param filterColumnIds Filter columnsID JSONarray
   * @return Updated version
   * @throws NoSuchMetricVersionException if the version does not exist
   */
  MetricVersion alterMetricVersion(
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
      throws
          NoSuchMetricVersionException; // ============================= MetricModifier management

  // =============================

  /**
   * List in pages schema Modifiers under namespace
   *
   * @param namespace schema namespace
   * @param offset offset
   * @param limit page size
   * @return Paginated results,Contains complete modifier data
   * @throws NoSuchSchemaException if schema does not exist
   */
  PagedResult<MetricModifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * Pass {@link NameIdentifier} from catalog Get indicator modifiers
   *
   * @param ident indicator modifier identifier
   * @return indicator modifier object
   */
  MetricModifier getMetricModifier(NameIdentifier ident);

  /**
   * in catalog Create indicator modifiers in
   *
   * @param ident Name identifier of the indicator modifier
   * @param code modifier encoding
   * @param comment Modifier annotation,Optional,can be null
   * @param modifierType modifier type,from value range,Optional,can be null
   * @return Created indicator modifier object
   * @throws NoSuchSchemaException if schema does not exist
   */
  MetricModifier createMetricModifier(
      NameIdentifier ident, String code, String comment, String modifierType)
      throws NoSuchSchemaException;

  /**
   * from catalog Remove modifier
   *
   * @param ident The name identifier of the modifier
   * @return Returns if modifier is removed true,Returns if modifier does not exist false
   */
  boolean deleteMetricModifier(NameIdentifier ident);

  /**
   * Modify modifier information
   *
   * @param ident The name identifier of the modifier
   * @param name Modifier name(Optional)
   * @param comment Modifier annotation(Optional)
   * @return Updated modifiers
   */
  MetricModifier alterMetricModifier(
      NameIdentifier ident,
      String name,
      String comment); // ============================= WordRoot management

  // =============================

  /**
   * List in pages schema Root words under namespace
   *
   * @param namespace schema namespace
   * @param offset offset
   * @param limit page size
   * @return Paginated results,Contains complete root data
   * @throws NoSuchSchemaException if schema does not exist
   */
  PagedResult<WordRoot> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * Pass {@link NameIdentifier} from catalog Get the root word
   *
   * @param ident root identifier
   * @return root object
   * @throws NoSuchWordRootException if the root does not exist
   */
  WordRoot getWordRoot(NameIdentifier ident) throws NoSuchWordRootException;

  /**
   * in catalog Create root words in
   *
   * @param ident root name identifier
   * @param code Root encoding
   * @param name Name
   * @param dataType data type,Such as VARCHAR(64)
   * @param comment Root annotation,Optional,can be null
   * @return Created stem object
   * @throws NoSuchSchemaException if schema does not exist
   * @throws WordRootAlreadyExistsException If the root already exists
   */
  WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException, WordRootAlreadyExistsException;

  /**
   * from catalog Remove root word
   *
   * @param ident root name identifier
   * @return Returns if root is removed true,Returns if root does not exist false
   */
  boolean deleteWordRoot(NameIdentifier ident);

  /**
   * Update root information
   *
   * @param ident root name identifier
   * @param name Name
   * @param dataType data type
   * @param comment Comment
   * @return updated stem object
   * @throws NoSuchWordRootException if the root does not exist
   */
  WordRoot alterWordRoot(NameIdentifier ident, String name, String dataType, String comment)
      throws NoSuchWordRootException; // ============================= Unit management

  // =============================

  /**
   * List in pages schema Units under namespace
   *
   * @param namespace schema namespace
   * @param offset offset
   * @param limit page size
   * @return Paginated results,Contains complete unit data
   * @throws NoSuchSchemaException if schema does not exist
   */
  PagedResult<Unit> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * Pass {@link NameIdentifier} from catalog Get unit
   *
   * @param ident unit identifier
   * @return unit object
   * @throws NoSuchUnitException If the unit does not exist
   */
  Unit getUnit(NameIdentifier ident) throws NoSuchUnitException;

  /**
   * in catalog Create units in
   *
   * @param ident Unit name identifier
   * @param code unit code
   * @param name Unit name
   * @param symbol unit symbol
   * @param comment Unit Notes,Optional,can be null
   * @return Created unit object
   * @throws NoSuchSchemaException if schema does not exist
   * @throws UnitAlreadyExistsException If the unit already exists
   */
  Unit createUnit(NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException, UnitAlreadyExistsException;

  /**
   * from catalog Delete unit
   *
   * @param ident Unit name identifier
   * @return Returns if unit is deleted true,Returns if unit does not exist false
   */
  boolean deleteUnit(NameIdentifier ident);

  /**
   * Update organization information
   *
   * @param ident Unit name identifier
   * @param name Unit name
   * @param symbol unit symbol
   * @param comment Comment
   * @return Updated unit object
   * @throws NoSuchUnitException If the unit does not exist
   */
  Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment)
      throws NoSuchUnitException; // ==================== ValueDomain Range related methods

  // ====================

  /**
   * List in pages schema Value range under namespace
   *
   * @param namespace schema namespace
   * @param offset offset
   * @param limit page size
   * @return Paginated results,Contains complete range data
   * @throws NoSuchSchemaException if schema does not exist
   */
  PagedResult<ValueDomain> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * Pass {@link NameIdentifier} from catalog Get value range
   *
   * @param ident range identifier (domainCode)
   * @return range object
   * @throws NoSuchValueDomainException If the value range does not exist
   */
  ValueDomain getValueDomain(NameIdentifier ident) throws NoSuchValueDomainException;

  /**
   * in catalog Create a value range in
   *
   * @param ident The name identifier of the value field (domainCode)
   * @param domainCode range encoding
   * @param domainName Value field name
   * @param domainType Range type (ENUM/RANGE/REGEX)
   * @param domainLevel range level (BUILTIN/BUSINESS)
   * @param items List of range items
   * @param comment Range annotation,Optional,can be null
   * @param dataType Value range data type,Such as STRING,INTEGER Wait
   * @return Created value range object
   * @throws NoSuchSchemaException if schema does not exist
   * @throws ValueDomainAlreadyExistsException If the value range already exists
   */
  ValueDomain createValueDomain(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      ValueDomain.Type domainType,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchSchemaException, ValueDomainAlreadyExistsException;

  /**
   * from catalog Delete range
   *
   * @param ident The name identifier of the value field
   * @return Returns if the range is deleted true,Returns if the value range does not exist false
   */
  boolean deleteValueDomain(NameIdentifier ident);

  /**
   * Update range information
   *
   * @param ident The name identifier of the value field
   * @param domainName Value field name
   * @param domainLevel range level
   * @param items List of range items
   * @param comment Comment
   * @param dataType Value range data type
   * @return updated value range object
   * @throws NoSuchValueDomainException If the value range does not exist
   */
  ValueDomain alterValueDomain(
      NameIdentifier ident,
      String domainName,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchValueDomainException;
}
