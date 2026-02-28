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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTenantSqlRewriter {

  private static final TenantSqlPatchRegistry PATCH_REGISTRY = new TenantSqlPatchRegistry();

  @Test
  public void testRewriteSimpleSelectSql() {
    String sql = "SELECT catalog_id FROM catalog_meta WHERE deleted_at = 0";

    String rewritten =
        TenantSqlRewriter.rewrite(
            "org.apache.gravitino.storage.relational.mapper.CatalogMetaMapper.test",
            sql,
            7L,
            PATCH_REGISTRY);

    Assertions.assertTrue(rewritten.contains("catalog_meta.tenant_id = 7"));
  }

  @Test
  public void testRewriteJoinSqlWithBothSideTenantCheck() {
    String sql =
        "SELECT ro.role_id FROM role_meta ro JOIN user_role_rel re ON ro.role_id = re.role_id "
            + "WHERE re.user_id = ? AND ro.deleted_at = 0 AND re.deleted_at = 0";

    String rewritten =
        TenantSqlRewriter.rewrite(
            "org.apache.gravitino.storage.relational.mapper.RoleMetaMapper.listRolesByUserId",
            sql,
            9L,
            PATCH_REGISTRY);

    Assertions.assertTrue(rewritten.contains("ro.tenant_id = 9"));
    Assertions.assertTrue(rewritten.contains("re.tenant_id = 9"));
    Assertions.assertTrue(rewritten.contains("ro.tenant_id = re.tenant_id"));
  }

  @Test
  public void testRewriteInsertSql() {
    String sql =
        "INSERT INTO user_meta(user_id, user_name, metalake_id, audit_info, current_version, "
            + "last_version, deleted_at) VALUES(?, ?, ?, ?, ?, ?, ?)";

    String rewritten =
        TenantSqlRewriter.rewrite(
            "org.apache.gravitino.storage.relational.mapper.UserMetaMapper.insertUserMeta",
            sql,
            3L,
            PATCH_REGISTRY);

    Assertions.assertTrue(rewritten.contains("tenant_id"));
    Assertions.assertTrue(rewritten.contains(", 3)"));
  }

  @Test
  public void testUnregisteredComplexSqlShouldFailFast() {
    String sql =
        "SELECT id FROM user_meta WHERE deleted_at = 0 UNION SELECT id FROM role_meta WHERE deleted_at = 0";

    IllegalStateException exception =
        Assertions.assertThrows(
            IllegalStateException.class,
            () ->
                TenantSqlRewriter.rewrite(
                    "org.apache.gravitino.storage.relational.mapper.UserMetaMapper.unionQuery",
                    sql,
                    1L,
                    PATCH_REGISTRY));

    Assertions.assertTrue(exception.getMessage().contains("TenantSqlPatchRegistry"));
  }

  @Test
  public void testRegisteredComplexSqlShouldApplyPatch() {
    String sql =
        "SELECT owner_id FROM owner_meta WHERE deleted_at = 0 "
            + "UNION SELECT tag_id FROM tag_meta WHERE deleted_at = 0";

    String rewritten =
        TenantSqlRewriter.rewrite(
            "org.apache.gravitino.storage.relational.mapper.OwnerMetaMapper.listOwnersByObject",
            sql,
            11L,
            PATCH_REGISTRY);

    Assertions.assertTrue(rewritten.contains("tenant_id = 11"));
  }
}
