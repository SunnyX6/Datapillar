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

import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * Unit 接口表示单位（指标的度量单位）
 *
 * <p>单位是企业数仓中的通用基础设施，用于统一指标的度量单位，例如：
 *
 * <ul>
 *   <li>CURRENCY - 人民币 (¥)
 *   <li>RATIO - 百分比 (%)
 *   <li>COUNT - 个数 (个)
 * </ul>
 */
@Evolving
public interface Unit extends Auditable {

  /**
   * 获取单位编码
   *
   * @return 单位编码，如 CURRENCY, RATIO, COUNT
   */
  String code();

  /**
   * 获取单位名称
   *
   * @return 单位名称，如 人民币, 百分比, 个数
   */
  String name();

  /**
   * 获取单位符号
   *
   * @return 单位符号，如 ¥, %, 个
   */
  String symbol();

  /**
   * 获取单位注释
   *
   * @return 单位注释
   */
  String comment();
}
