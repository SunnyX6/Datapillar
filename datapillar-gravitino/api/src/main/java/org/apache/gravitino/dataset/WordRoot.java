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
 * WordRoot 接口表示词根（数仓命名规范中的基础词汇）
 *
 * <p>词根是企业数仓中的通用基础设施，用于统一命名规范，例如：
 *
 * <ul>
 *   <li>amt - 金额 (amount)
 *   <li>cnt - 数量 (count)
 *   <li>rate - 比率 (rate)
 *   <li>qty - 数量 (quantity)
 * </ul>
 */
@Evolving
public interface WordRoot extends Auditable {

  /**
   * 获取词根编码
   *
   * @return 词根编码，如 amt, cnt, rate
   */
  String code();

  /**
   * 获取词根中文名称
   *
   * @return 词根中文名称，如 金额, 数量, 比率
   */
  String nameCn();

  /**
   * 获取词根英文名称
   *
   * @return 词根英文名称，如 amount, count, rate
   */
  String nameEn();

  /**
   * 获取词根注释
   *
   * @return 词根注释
   */
  String comment();
}
