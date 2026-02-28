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
package org.apache.gravitino.datapillar.storage.relational;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** Registry for complex SQL statements that require explicit tenant rewrite patching. */
public class TenantSqlPatchRegistry {

  private static final Set<String> REGISTERED_COMPLEX_SQL_PREFIXES =
      ImmutableSet.of(
          "org.apache.gravitino.storage.relational.mapper.OwnerMetaMapper.",
          "org.apache.gravitino.storage.relational.mapper.SecurableObjectMapper.",
          "org.apache.gravitino.storage.relational.mapper.TagMetadataObjectRelMapper.",
          "org.apache.gravitino.storage.relational.mapper.StatisticMetaMapper.",
          "org.apache.gravitino.storage.relational.mapper.TableColumnMapper.",
          "org.apache.gravitino.storage.relational.mapper.ModelVersionAliasRelMapper.");

  private static final Set<String> REGISTERED_COMPLEX_SQL_STATEMENTS =
      ImmutableSet.of(
          "org.apache.gravitino.storage.relational.mapper.UserMetaMapper.listExtendedUserPOsByMetalakeId",
          "org.apache.gravitino.storage.relational.mapper.GroupMetaMapper.listExtendedGroupPOsByMetalakeId");

  public boolean registered(String statementId) {
    if (statementId == null || statementId.isEmpty()) {
      return false;
    }

    if (REGISTERED_COMPLEX_SQL_STATEMENTS.contains(statementId)) {
      return true;
    }

    return REGISTERED_COMPLEX_SQL_PREFIXES.stream().anyMatch(statementId::startsWith);
  }
}
