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

import javax.annotation.Nullable;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * MetricModifier 接口表示指标修饰符
 *
 * <p>修饰符用于对指标进行分类和标记，修饰符类型通过关联值域（ValueDomain）来定义
 */
@Evolving
public interface MetricModifier extends Auditable {

  /**
   * 获取修饰符名称
   *
   * @return 修饰符名称
   */
  String name();

  /**
   * 获取修饰符编码
   *
   * @return 修饰符编码
   */
  String code();

  /**
   * 获取修饰符注释
   *
   * @return 修饰符注释
   */
  String comment();

  /**
   * 获取修饰符类型，来自值域
   *
   * @return 修饰符类型，可为空
   */
  @Nullable
  String modifierType();
}
