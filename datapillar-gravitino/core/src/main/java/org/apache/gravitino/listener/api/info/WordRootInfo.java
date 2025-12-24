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
import org.apache.gravitino.dataset.WordRoot;

/** WordRootInfo exposes word root information for event listener, it's supposed to be read only. */
@DeveloperApi
public class WordRootInfo {
  private final String code;
  private final Optional<String> name;
  private final Optional<String> dataType;
  private final Optional<String> comment;
  private final Optional<Audit> audit;

  /**
   * Constructs a {@link WordRootInfo} instance based on a given word root.
   *
   * @param wordRoot the word root to expose information for.
   */
  public WordRootInfo(WordRoot wordRoot) {
    this(
        wordRoot.code(),
        wordRoot.name(),
        wordRoot.dataType(),
        wordRoot.comment(),
        wordRoot.auditInfo());
  }

  /**
   * Constructs a {@link WordRootInfo} instance based on all fields.
   *
   * @param code the code of the word root.
   * @param name the name of the word root.
   * @param dataType the data type of the word root.
   * @param comment the comment of the word root.
   * @param audit the audit information of the word root.
   */
  public WordRootInfo(String code, String name, String dataType, String comment, Audit audit) {
    this.code = code;
    this.name = Optional.ofNullable(name);
    this.dataType = Optional.ofNullable(dataType);
    this.comment = Optional.ofNullable(comment);
    this.audit = Optional.ofNullable(audit);
  }

  /**
   * Returns the code of the word root.
   *
   * @return the code of the word root.
   */
  public String code() {
    return code;
  }

  /**
   * Returns the name of the word root.
   *
   * @return the name of the word root or empty if not set.
   */
  public Optional<String> name() {
    return name;
  }

  /**
   * Returns the data type of the word root.
   *
   * @return the data type of the word root or empty if not set.
   */
  public Optional<String> dataType() {
    return dataType;
  }

  /**
   * Returns the comment of the word root.
   *
   * @return the comment of the word root or empty if not set.
   */
  public Optional<String> comment() {
    return comment;
  }

  /**
   * Returns the audit information of the word root.
   *
   * @return the audit information of the word root or empty if not set.
   */
  public Optional<Audit> audit() {
    return audit;
  }
}
