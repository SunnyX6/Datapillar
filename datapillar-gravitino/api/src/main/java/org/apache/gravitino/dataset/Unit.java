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
 * Unit Interface representation unit（The unit of measurement for the indicator）
 *
 * <p>Units are common infrastructure in enterprise data warehouses，The unit of measurement used to
 * unify the metric，For example：
 *
 * <ul>
 *   <li>CURRENCY - RMB (¥)
 *   <li>RATIO - Percentage (%)
 *   <li>COUNT - number (a)
 * </ul>
 */
@Evolving
public interface Unit extends Auditable {

  /**
   * Get unit code
   *
   * @return unit code，Such as CURRENCY, RATIO, COUNT
   */
  String code();

  /**
   * Get unit name
   *
   * @return Unit name，Such as RMB, Percentage, number
   */
  String name();

  /**
   * Get unit symbol
   *
   * @return unit symbol，Such as ¥, %, a
   */
  String symbol();

  /**
   * Get unit notes
   *
   * @return Unit Notes
   */
  String comment();
}
