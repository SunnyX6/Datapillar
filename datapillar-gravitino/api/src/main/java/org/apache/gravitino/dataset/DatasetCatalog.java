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
 * DatasetCatalog 接口定义了在 schema 中管理数据集对象（Metric、WordRoot 等）的公共 API。 如果 catalog 实现支持数据集对象，则应实现此接口。
 */
@Evolving
public interface DatasetCatalog {

  // ============================= Metric 管理 =============================

  /**
   * 分页列出 schema 命名空间下的指标
   *
   * @param namespace schema 命名空间
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 分页结果
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  PagedResult<NameIdentifier> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * 通过 {@link NameIdentifier} 从 catalog 获取指标元数据
   *
   * @param ident 指标标识符
   * @return 指标元数据
   * @throws NoSuchMetricException 如果指标不存在
   */
  Metric getMetric(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * 使用 {@link NameIdentifier} 检查指标是否存在
   *
   * @param ident 指标标识符
   * @return 如果指标存在则返回 true，否则返回 false
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
   * 在 catalog 中注册指标
   *
   * @param ident 指标的名称标识符
   * @param code 指标编码
   * @param type 指标类型
   * @param dataType 数据类型，如 DECIMAL(18,2)
   * @param comment 指标注释，可选，可为 null
   * @param properties 指标属性，可选，可为 null 或空
   * @param unit 指标单位，可选，可为 null
   * @param aggregationLogic 聚合逻辑（如SUM/COUNT/AVG），可选，可为 null
   * @param parentMetricIds 父指标ID数组（用于派生/复合指标），可选，可为 null
   * @param calculationFormula 计算公式（用于复合指标），可选，可为 null
   * @param refCatalogName 引用的数据源名称（原子指标用），可选，可为 null
   * @param refSchemaName 引用的数据库名称（原子指标用），可选，可为 null
   * @param refTableName 引用的表名称（原子指标用），可选，可为 null
   * @param measureColumns 度量列JSON数组，可选，可为 null
   * @param filterColumns 过滤列JSON数组，可选，可为 null
   * @return 注册的指标对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws MetricAlreadyExistsException 如果指标已存在
   */
  Metric registerMetric(
      NameIdentifier ident,
      String code,
      Metric.Type type,
      String dataType,
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumns,
      String filterColumns)
      throws NoSuchSchemaException, MetricAlreadyExistsException;

  /**
   * 从 catalog 删除指标。如果指标不存在，返回 false。 删除指标将同时删除链接到此指标的所有版本。
   *
   * @param ident 指标的名称标识符
   * @return 如果删除指标则返回 true，如果指标不存在则返回 false
   */
  boolean deleteMetric(NameIdentifier ident);

  /**
   * 对 catalog 中的指标应用 {@link MetricChange 变更}
   *
   * @param ident 要修改的指标的 {@link NameIdentifier} 实例
   * @param changes 要应用于指标的 {@link MetricChange} 实例
   * @return 更新后的 {@link Metric} 实例
   * @throws NoSuchMetricException 如果指标不存在
   * @throws IllegalArgumentException 如果变更被实现拒绝
   */
  Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException;

  // ============================= MetricVersion 管理 =============================

  /**
   * 通过 {@link NameIdentifier} 列出已注册指标的所有版本
   *
   * @param ident 指标的名称标识符
   * @return 指标的版本号数组
   * @throws NoSuchMetricException 如果指标不存在
   */
  int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * 通过 {@link NameIdentifier} 列出已注册指标的所有版本及其信息
   *
   * @param ident 指标的名称标识符
   * @return 指标的版本信息数组
   * @throws NoSuchMetricException 如果指标不存在
   */
  MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException;

  /**
   * 通过 {@link NameIdentifier} 和版本号从 catalog 获取指标版本
   *
   * @param ident 指标的名称标识符
   * @param version 指标的版本号
   * @return 指标版本对象
   * @throws NoSuchMetricVersionException 如果指标版本不存在
   */
  MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException;

  /**
   * 通过 {@link NameIdentifier} 和版本号检查指标版本是否存在
   *
   * @param ident 指标的名称标识符
   * @param version 指标的版本号
   * @return 如果指标版本存在则返回 true，否则返回 false
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
   * 通过 {@link NameIdentifier} 和版本号删除指标版本
   *
   * @param ident 指标的名称标识符
   * @param version 指标的版本号
   * @return 如果删除指标版本则返回 true，如果指标版本不存在则返回 false
   */
  boolean deleteMetricVersion(NameIdentifier ident, int version);

  /**
   * 切换指标的当前版本到指定版本
   *
   * @param ident 指标的名称标识符
   * @param targetVersion 目标版本号
   * @return 更新后的 {@link Metric} 实例
   * @throws NoSuchMetricException 如果指标不存在
   * @throws NoSuchMetricVersionException 如果目标版本不存在
   * @throws IllegalArgumentException 如果目标版本号无效或等于当前版本
   */
  Metric switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException;

  /**
   * 为指标创建新版本（版本号自动递增）
   *
   * @param ident 指标的名称标识符
   * @param comment 版本注释
   * @param unit 指标单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标ID数组
   * @param calculationFormula 计算公式
   * @return 新创建的版本
   * @throws NoSuchMetricException 如果指标不存在
   */
  MetricVersion linkMetricVersion(
      NameIdentifier ident,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws NoSuchMetricException;

  /**
   * 修改指定版本的信息
   *
   * @param ident 指标的名称标识符
   * @param version 版本号
   * @param comment 版本注释
   * @param unit 指标单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标ID数组
   * @param calculationFormula 计算公式
   * @return 更新后的版本
   * @throws NoSuchMetricVersionException 如果版本不存在
   */
  MetricVersion alterMetricVersion(
      NameIdentifier ident,
      int version,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws NoSuchMetricVersionException;

  // ============================= MetricModifier 管理 =============================

  /**
   * 分页列出 schema 命名空间下的修饰符
   *
   * @param namespace schema 命名空间
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 分页结果
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  PagedResult<NameIdentifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * 通过 {@link NameIdentifier} 从 catalog 获取指标修饰符
   *
   * @param ident 指标修饰符标识符
   * @return 指标修饰符对象
   */
  MetricModifier getMetricModifier(NameIdentifier ident);

  /**
   * 在 catalog 中创建指标修饰符
   *
   * @param ident 指标修饰符的名称标识符
   * @param code 修饰符编码
   * @param type 修饰符类型
   * @param comment 修饰符注释，可选，可为 null
   * @return 创建的指标修饰符对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  MetricModifier createMetricModifier(
      NameIdentifier ident, String code, MetricModifier.Type type, String comment)
      throws NoSuchSchemaException;

  /**
   * 从 catalog 删除修饰符
   *
   * @param ident 修饰符的名称标识符
   * @return 如果删除修饰符则返回 true，如果修饰符不存在则返回 false
   */
  boolean deleteMetricModifier(NameIdentifier ident);

  /**
   * 修改修饰符信息
   *
   * @param ident 修饰符的名称标识符
   * @param type 修饰符类型
   * @param comment 修饰符注释
   * @return 更新后的修饰符
   */
  MetricModifier alterMetricModifier(
      NameIdentifier ident, MetricModifier.Type type, String comment);

  // ============================= WordRoot 管理 =============================

  /**
   * 分页列出 schema 命名空间下的词根
   *
   * @param namespace schema 命名空间
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 分页结果
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  PagedResult<NameIdentifier> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * 通过 {@link NameIdentifier} 从 catalog 获取词根
   *
   * @param ident 词根标识符
   * @return 词根对象
   * @throws NoSuchWordRootException 如果词根不存在
   */
  WordRoot getWordRoot(NameIdentifier ident) throws NoSuchWordRootException;

  /**
   * 在 catalog 中创建词根
   *
   * @param ident 词根的名称标识符
   * @param code 词根编码
   * @param name 名称
   * @param dataType 数据类型，如 VARCHAR(64)
   * @param comment 词根注释，可选，可为 null
   * @return 创建的词根对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws WordRootAlreadyExistsException 如果词根已存在
   */
  WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException, WordRootAlreadyExistsException;

  /**
   * 从 catalog 删除词根
   *
   * @param ident 词根的名称标识符
   * @return 如果删除词根则返回 true，如果词根不存在则返回 false
   */
  boolean deleteWordRoot(NameIdentifier ident);

  /**
   * 更新词根信息
   *
   * @param ident 词根的名称标识符
   * @param name 名称
   * @param dataType 数据类型
   * @param comment 注释
   * @return 更新后的词根对象
   * @throws NoSuchWordRootException 如果词根不存在
   */
  WordRoot alterWordRoot(NameIdentifier ident, String name, String dataType, String comment)
      throws NoSuchWordRootException;

  // ============================= Unit 管理 =============================

  /**
   * 分页列出 schema 命名空间下的单位
   *
   * @param namespace schema 命名空间
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 分页结果
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  PagedResult<NameIdentifier> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * 通过 {@link NameIdentifier} 从 catalog 获取单位
   *
   * @param ident 单位标识符
   * @return 单位对象
   * @throws NoSuchUnitException 如果单位不存在
   */
  Unit getUnit(NameIdentifier ident) throws NoSuchUnitException;

  /**
   * 在 catalog 中创建单位
   *
   * @param ident 单位的名称标识符
   * @param code 单位编码
   * @param name 单位名称
   * @param symbol 单位符号
   * @param comment 单位注释，可选，可为 null
   * @return 创建的单位对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws UnitAlreadyExistsException 如果单位已存在
   */
  Unit createUnit(NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException, UnitAlreadyExistsException;

  /**
   * 从 catalog 删除单位
   *
   * @param ident 单位的名称标识符
   * @return 如果删除单位则返回 true，如果单位不存在则返回 false
   */
  boolean deleteUnit(NameIdentifier ident);

  /**
   * 更新单位信息
   *
   * @param ident 单位的名称标识符
   * @param name 单位名称
   * @param symbol 单位符号
   * @param comment 注释
   * @return 更新后的单位对象
   * @throws NoSuchUnitException 如果单位不存在
   */
  Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment)
      throws NoSuchUnitException;

  // ==================== ValueDomain 值域相关方法 ====================

  /**
   * 分页列出 schema 命名空间下的值域
   *
   * @param namespace schema 命名空间
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 分页结果
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  PagedResult<NameIdentifier> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException;

  /**
   * 通过 {@link NameIdentifier} 从 catalog 获取值域
   *
   * @param ident 值域标识符 (格式: domainCode:itemValue)
   * @return 值域对象
   * @throws NoSuchValueDomainException 如果值域不存在
   */
  ValueDomain getValueDomain(NameIdentifier ident) throws NoSuchValueDomainException;

  /**
   * 在 catalog 中创建值域项
   *
   * @param ident 值域的名称标识符 (格式: domainCode:itemValue)
   * @param domainCode 值域编码
   * @param domainName 值域名称
   * @param domainType 值域类型 (ENUM/RANGE/REGEX)
   * @param itemValue 值域项值
   * @param itemLabel 值域项标签
   * @param comment 值域注释，可选，可为 null
   * @return 创建的值域对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws ValueDomainAlreadyExistsException 如果值域项已存在
   */
  ValueDomain createValueDomain(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      ValueDomain.Type domainType,
      String itemValue,
      String itemLabel,
      String comment)
      throws NoSuchSchemaException, ValueDomainAlreadyExistsException;

  /**
   * 从 catalog 删除值域项
   *
   * @param ident 值域的名称标识符
   * @return 如果删除值域则返回 true，如果值域不存在则返回 false
   */
  boolean deleteValueDomain(NameIdentifier ident);

  /**
   * 更新值域项信息
   *
   * @param ident 值域的名称标识符
   * @param domainName 值域名称
   * @param itemLabel 值域项标签
   * @param comment 注释
   * @return 更新后的值域对象
   * @throws NoSuchValueDomainException 如果值域不存在
   */
  ValueDomain alterValueDomain(
      NameIdentifier ident, String domainName, String itemLabel, String comment)
      throws NoSuchValueDomainException;
}
