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
import org.apache.gravitino.exceptions.NoSuchWordRootException;
import org.apache.gravitino.exceptions.WordRootAlreadyExistsException;

/**
 * DatasetCatalog 接口定义了在 schema 中管理数据集对象（Metric、WordRoot 等）的公共 API。 如果 catalog 实现支持数据集对象，则应实现此接口。
 */
@Evolving
public interface DatasetCatalog {

  // ============================= Metric 管理 =============================

  /**
   * 从 catalog 中列出 schema 命名空间下的所有指标
   *
   * @param namespace schema 命名空间
   * @return 命名空间中的指标标识符数组
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  NameIdentifier[] listMetrics(Namespace namespace) throws NoSuchSchemaException;

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
   * @param comment 指标注释，可选，可为 null
   * @param properties 指标属性，可选，可为 null 或空
   * @param unit 指标单位，可选，可为 null
   * @param aggregationLogic 聚合逻辑（如SUM/COUNT/AVG），可选，可为 null
   * @param parentMetricIds 父指标ID数组（用于派生/复合指标），可选，可为 null
   * @param calculationFormula 计算公式（用于复合指标），可选，可为 null
   * @return 注册的指标对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws MetricAlreadyExistsException 如果指标已存在
   */
  Metric registerMetric(
      NameIdentifier ident,
      String code,
      Metric.Type type,
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
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

  // ============================= MetricModifier 管理 =============================

  /**
   * 从 catalog 中列出 schema 命名空间下的所有修饰符
   *
   * @param namespace schema 命名空间
   * @return 命名空间中的修饰符标识符数组
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  NameIdentifier[] listMetricModifiers(Namespace namespace) throws NoSuchSchemaException;

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

  // ============================= WordRoot 管理 =============================

  /**
   * 从 catalog 中列出 schema 命名空间下的所有词根
   *
   * @param namespace schema 命名空间
   * @return 命名空间中的词根标识符数组
   * @throws NoSuchSchemaException 如果 schema 不存在
   */
  NameIdentifier[] listWordRoots(Namespace namespace) throws NoSuchSchemaException;

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
   * @param nameCn 中文名称
   * @param nameEn 英文名称
   * @param comment 词根注释，可选，可为 null
   * @return 创建的词根对象
   * @throws NoSuchSchemaException 如果 schema 不存在
   * @throws WordRootAlreadyExistsException 如果词根已存在
   */
  WordRoot createWordRoot(
      NameIdentifier ident, String code, String nameCn, String nameEn, String comment)
      throws NoSuchSchemaException, WordRootAlreadyExistsException;

  /**
   * 从 catalog 删除词根
   *
   * @param ident 词根的名称标识符
   * @return 如果删除词根则返回 true，如果词根不存在则返回 false
   */
  boolean deleteWordRoot(NameIdentifier ident);
}
