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
package org.apache.gravitino.metric;

import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * MetricRoot 接口表示指标词根
 *
 * <p>词根用于组织指标的命名和分类
 */
@Evolving
public interface MetricRoot extends Auditable {

  /**
   * 获取根节点编码
   *
   * @return 根节点编码
   */
  String code();

  /**
   * 获取根节点中文名称
   *
   * @return 根节点中文名称
   */
  String nameCn();

  /**
   * 获取根节点英文名称
   *
   * @return 根节点英文名称
   */
  String nameEn();

  /**
   * 获取根节点注释
   *
   * @return 根节点注释
   */
  String comment();
}
