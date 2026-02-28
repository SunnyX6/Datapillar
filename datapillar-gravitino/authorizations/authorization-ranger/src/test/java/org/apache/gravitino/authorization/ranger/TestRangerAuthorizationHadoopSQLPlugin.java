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
package org.apache.gravitino.authorization.ranger;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.authorization.AuthorizationSecurableObject;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.authorization.Privileges;
import org.apache.gravitino.authorization.SecurableObject;
import org.apache.gravitino.authorization.SecurableObjects;
import org.apache.gravitino.authorization.ranger.RangerPrivileges.RangerHadoopSQLPrivilege;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.apache.gravitino.exceptions.AuthorizationPluginException;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestRangerAuthorizationHadoopSQLPlugin {

  @AfterEach
  public void clearTenantContext() {
    TenantContextHolder.remove();
  }

  @Test
  public void testColumnPrivilegesMapping() {
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();

    Set<?> selectColumnPrivileges =
        plugin.privilegesMappingRule().get(Privilege.Name.SELECT_COLUMN);
    Set<?> modifyColumnPrivileges =
        plugin.privilegesMappingRule().get(Privilege.Name.MODIFY_COLUMN);

    Assertions.assertTrue(selectColumnPrivileges.contains(RangerHadoopSQLPrivilege.READ));
    Assertions.assertTrue(selectColumnPrivileges.contains(RangerHadoopSQLPrivilege.SELECT));
    Assertions.assertTrue(modifyColumnPrivileges.contains(RangerHadoopSQLPrivilege.UPDATE));
    Assertions.assertTrue(modifyColumnPrivileges.contains(RangerHadoopSQLPrivilege.ALTER));
    Assertions.assertTrue(modifyColumnPrivileges.contains(RangerHadoopSQLPrivilege.WRITE));
  }

  @Test
  public void testTranslateColumnPrivilegeToPreciseColumnResource() {
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();

    SecurableObject columnObject =
        SecurableObjects.parse(
            "catalog.schema.table.col1",
            MetadataObject.Type.COLUMN,
            Lists.newArrayList(Privileges.SelectColumn.allow()));
    List<AuthorizationSecurableObject> objects = plugin.translatePrivilege(columnObject);

    Assertions.assertEquals(1, objects.size());
    Assertions.assertEquals(RangerHadoopSQLMetadataObject.Type.COLUMN, objects.get(0).type());
    Assertions.assertEquals(Lists.newArrayList("schema", "table", "col1"), objects.get(0).names());
  }

  @Test
  public void testRejectCrossGrainPrivilegeTranslation() {
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();

    SecurableObject tableWithColumnPrivilege =
        SecurableObjects.parse(
            "catalog.schema.table",
            MetadataObject.Type.TABLE,
            Lists.newArrayList(Privileges.SelectColumn.allow()));
    Assertions.assertThrows(
        AuthorizationPluginException.class,
        () -> plugin.translatePrivilege(tableWithColumnPrivilege));

    SecurableObject columnWithTablePrivilege =
        SecurableObjects.parse(
            "catalog.schema.table.col1",
            MetadataObject.Type.COLUMN,
            Lists.newArrayList(Privileges.SelectTable.allow()));
    Assertions.assertThrows(
        AuthorizationPluginException.class,
        () -> plugin.translatePrivilege(columnWithTablePrivilege));
  }

  @Test
  public void testTablePrivilegeDoesNotGenerateColumnWildcardResource() {
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();

    SecurableObject tableObject =
        SecurableObjects.parse(
            "catalog.schema.table",
            MetadataObject.Type.TABLE,
            Lists.newArrayList(Privileges.SelectTable.allow()));
    List<AuthorizationSecurableObject> objects = plugin.translatePrivilege(tableObject);

    Assertions.assertEquals(1, objects.size());
    Assertions.assertEquals(RangerHadoopSQLMetadataObject.Type.TABLE, objects.get(0).type());
  }

  @Test
  public void testPolicyNameShouldContainTenantPrefix() {
    TenantContextHolder.set(
        TenantContext.builder()
            .withTenantId(8L)
            .withTenantCode("t8")
            .withTenantName("Tenant-8")
            .build());
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();
    RangerHadoopSQLMetadataObject metadataObject =
        new RangerHadoopSQLMetadataObject(
            "schema", "table", RangerHadoopSQLMetadataObject.Type.TABLE);

    RangerPolicy policy = plugin.createPolicyAddResources(metadataObject);
    Assertions.assertEquals("t8:schema.table", policy.getName());
  }

  @Test
  public void testPolicyNameShouldFallbackToTenantZeroWithoutContext() {
    RangerAuthorizationHadoopSQLPlugin plugin = createPlugin();
    RangerHadoopSQLMetadataObject metadataObject =
        new RangerHadoopSQLMetadataObject(
            "schema", "table", RangerHadoopSQLMetadataObject.Type.TABLE);

    RangerPolicy policy = plugin.createPolicyAddResources(metadataObject);
    Assertions.assertEquals("t0:schema.table", policy.getName());
  }

  private RangerAuthorizationHadoopSQLPlugin createPlugin() {
    return Mockito.mock(RangerAuthorizationHadoopSQLPlugin.class, Mockito.CALLS_REAL_METHODS);
  }
}
