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

import java.util.List;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * ValueDomain Interface represents value range(Standardized constraints on data value ranges)
 *
 * <p>A value domain defines a data attribute"What is allowed to be filled in?",Support three
 * types:*
 *
 * <ul>
 *   <li>ENUM - enumeration value range:Such as order status [INIT,PAID,SHIPPED,COMPLETED,CANCELLED]
 *   <li>RANGE - Interval value range:such as probability interval [0,1]
 *   <li>REGEX - pattern value range:Such as ID card verification rules
 * </ul>
 *
 * <p>The value range can be passed Tag The mechanism is arbitrary MetadataObject
 * Quote(column,table,metric Wait)
 */
@Evolving
public interface ValueDomain extends Auditable {

  /** Value range type enum */
  enum Type {
    /** enumeration type:Define a discrete set of optional values */
    ENUM,
    /** Interval:Define numerical range */
    RANGE,
    /** pattern:Define regular expression constraints */
    REGEX
  }

  /** Range level enumeration */
  enum Level {
    /** Built-in:System predefined,Cannot be deleted */
    BUILTIN,
    /** Business:User defined,Can be added,deleted or modified */
    BUSINESS
  }

  /** range item(enumeration value/interval/regular) */
  interface Item {
    /**
     * Get value
     *
     * @return value
     */
    String value();

    /**
     * Get tag(display name)
     *
     * @return label
     */
    String label();
  }

  /**
   * Get range encoding
   *
   * @return range encoding,Such as ORDER_STATUS,PROBABILITY,ID_CARD
   */
  String domainCode();

  /**
   * Get the value field name
   *
   * @return Value field name,Such as Order status value field,probability interval,ID card
   *     verification code
   */
  String domainName();

  /**
   * Get the value field type
   *
   * @return Range type:ENUM,RANGE,REGEX
   */
  Type domainType();

  /**
   * Get the range level
   *
   * @return range level:BUILTIN(Built-in),BUSINESS(Business)
   */
  Level domainLevel();

  /**
   * Get a list of value range items
   *
   * @return List of range items(enumeration value/interval expression/regular)
   */
  List<Item> items();

  /**
   * Get range annotation
   *
   * @return Range annotation
   */
  String comment();

  /**
   * Get the value range data type
   *
   * @return Value range data type,Such as STRING,INTEGER,DECIMAL Wait
   */
  String dataType();
}
