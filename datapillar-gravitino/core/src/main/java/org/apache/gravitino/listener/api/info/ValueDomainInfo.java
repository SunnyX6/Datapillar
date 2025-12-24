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

package org.apache.gravitino.listener.api.info;

import java.util.Optional;
import org.apache.gravitino.Audit;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.dataset.ValueDomain;

/**
 * ValueDomainInfo exposes value domain information for event listener, it's supposed to be read
 * only.
 */
@DeveloperApi
public class ValueDomainInfo {
  private final String domainCode;
  private final Optional<String> domainName;
  private final ValueDomain.Type domainType;
  private final String itemValue;
  private final Optional<String> itemLabel;
  private final Optional<String> comment;
  private final Optional<Audit> audit;

  /**
   * Constructs a {@link ValueDomainInfo} instance based on a given value domain.
   *
   * @param valueDomain the value domain to expose information for.
   */
  public ValueDomainInfo(ValueDomain valueDomain) {
    this(
        valueDomain.domainCode(),
        valueDomain.domainName(),
        valueDomain.domainType(),
        valueDomain.itemValue(),
        valueDomain.itemLabel(),
        valueDomain.comment(),
        valueDomain.auditInfo());
  }

  /**
   * Constructs a {@link ValueDomainInfo} instance based on all fields.
   *
   * @param domainCode the code of the value domain.
   * @param domainName the name of the value domain.
   * @param domainType the type of the value domain.
   * @param itemValue the item value.
   * @param itemLabel the item label.
   * @param comment the comment of the value domain.
   * @param audit the audit information of the value domain.
   */
  public ValueDomainInfo(
      String domainCode,
      String domainName,
      ValueDomain.Type domainType,
      String itemValue,
      String itemLabel,
      String comment,
      Audit audit) {
    this.domainCode = domainCode;
    this.domainName = Optional.ofNullable(domainName);
    this.domainType = domainType;
    this.itemValue = itemValue;
    this.itemLabel = Optional.ofNullable(itemLabel);
    this.comment = Optional.ofNullable(comment);
    this.audit = Optional.ofNullable(audit);
  }

  /**
   * Returns the code of the value domain.
   *
   * @return the code of the value domain.
   */
  public String domainCode() {
    return domainCode;
  }

  /**
   * Returns the name of the value domain.
   *
   * @return the name of the value domain or empty if not set.
   */
  public Optional<String> domainName() {
    return domainName;
  }

  /**
   * Returns the type of the value domain.
   *
   * @return the type of the value domain.
   */
  public ValueDomain.Type domainType() {
    return domainType;
  }

  /**
   * Returns the item value.
   *
   * @return the item value.
   */
  public String itemValue() {
    return itemValue;
  }

  /**
   * Returns the item label.
   *
   * @return the item label or empty if not set.
   */
  public Optional<String> itemLabel() {
    return itemLabel;
  }

  /**
   * Returns the comment of the value domain.
   *
   * @return the comment of the value domain or empty if not set.
   */
  public Optional<String> comment() {
    return comment;
  }

  /**
   * Returns the audit information of the value domain.
   *
   * @return the audit information of the value domain or empty if not set.
   */
  public Optional<Audit> audit() {
    return audit;
  }
}
