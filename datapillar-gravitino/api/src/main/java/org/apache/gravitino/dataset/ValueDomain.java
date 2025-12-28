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

import java.util.List;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * ValueDomain 接口表示值域（数据取值范围的标准化约束）
 *
 * <p>值域定义了某个数据属性"允许填什么"，支持三种类型：
 *
 * <ul>
 *   <li>ENUM - 枚举型值域：如订单状态 [INIT, PAID, SHIPPED, COMPLETED, CANCELLED]
 *   <li>RANGE - 区间型值域：如概率区间 [0, 1]
 *   <li>REGEX - 模式型值域：如身份证校验正则
 * </ul>
 *
 * <p>值域可以通过 Tag 机制被任意 MetadataObject 引用（column、table、metric 等）
 */
@Evolving
public interface ValueDomain extends Auditable {

  /** 值域类型枚举 */
  enum Type {
    /** 枚举型：定义一组离散的可选值 */
    ENUM,
    /** 区间型：定义数值范围 */
    RANGE,
    /** 模式型：定义正则表达式约束 */
    REGEX
  }

  /** 值域级别枚举 */
  enum Level {
    /** 内置：系统预定义，不可删除 */
    BUILTIN,
    /** 业务：用户自定义，可增删改 */
    BUSINESS
  }

  /** 值域项（枚举值/区间/正则） */
  interface Item {
    /**
     * 获取值
     *
     * @return 值
     */
    String value();

    /**
     * 获取标签（显示名称）
     *
     * @return 标签
     */
    String label();
  }

  /**
   * 获取值域编码
   *
   * @return 值域编码，如 ORDER_STATUS, PROBABILITY, ID_CARD
   */
  String domainCode();

  /**
   * 获取值域名称
   *
   * @return 值域名称，如 订单状态值域, 概率区间, 身份证校验码
   */
  String domainName();

  /**
   * 获取值域类型
   *
   * @return 值域类型：ENUM, RANGE, REGEX
   */
  Type domainType();

  /**
   * 获取值域级别
   *
   * @return 值域级别：BUILTIN（内置）, BUSINESS（业务）
   */
  Level domainLevel();

  /**
   * 获取值域项列表
   *
   * @return 值域项列表（枚举值/区间表达式/正则）
   */
  List<Item> items();

  /**
   * 获取值域注释
   *
   * @return 值域注释
   */
  String comment();

  /**
   * 获取值域数据类型
   *
   * @return 值域数据类型，如 STRING, INTEGER, DECIMAL 等
   */
  String dataType();
}
