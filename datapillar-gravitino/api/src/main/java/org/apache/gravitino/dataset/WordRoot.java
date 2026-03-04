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
 * WordRoot interface representation root（Basic vocabulary in data warehouse naming conventions）
 *
 * <p>Stem is the common infrastructure in enterprise data warehouses，Used to unify naming
 * conventions，For example：
 *
 * <ul>
 *   <li>amt - Amount
 *   <li>cnt - Quantity
 *   <li>rate - Ratio
 * </ul>
 */
@Evolving
public interface WordRoot extends Auditable {

  /**
   * Get root code
   *
   * @return Root encoding，Such as amt, cnt, rate
   */
  String code();

  /**
   * Get root name
   *
   * @return root name，Such as Amount, Quantity, Ratio（What the user inputs is what it is）
   */
  String name();

  /**
   * Get the root data type
   *
   * @return data type，Such as STRING, INTEGER, DECIMAL(10,2)，Returns if not set null
   */
  default String dataType() {
    return null;
  }

  /**
   * Get root annotation
   *
   * @return Root annotation
   */
  String comment();
}
