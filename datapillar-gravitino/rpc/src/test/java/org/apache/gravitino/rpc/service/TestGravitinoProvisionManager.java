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
package org.apache.gravitino.rpc.service;

import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegeCommand;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesRequest;
import com.sunny.datapillar.common.rpc.security.v1.GravitinoObjectType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.authorization.Privilege;
import org.apache.gravitino.rpc.adapter.gravitino.GravitinoClientAdapter;
import org.apache.gravitino.rpc.support.config.GravitinoRpcProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestGravitinoProvisionManager {

  @Test
  public void testColumnCommandExpandedByColumn() throws IOException {
    FakeGravitinoClientAdapter adapter = new FakeGravitinoClientAdapter();
    GravitinoProvisionManager manager = new GravitinoProvisionManager(adapter);
    GrantDataPrivilegesRequest request =
        GrantDataPrivilegesRequest.newBuilder()
            .setTenantId(1)
            .setMetalake("OneMeta")
            .addCommands(
                GrantDataPrivilegeCommand.newBuilder()
                    .setRoleName("dr_t1_u1")
                    .setObjectType(GravitinoObjectType.GRAVITINO_OBJECT_TYPE_COLUMN)
                    .setObjectName("OneDS.sales.orders")
                    .addColumnNames("amount")
                    .addColumnNames("order_id")
                    .addPrivilegeNames("SELECT_COLUMN")
                    .build())
            .build();

    GravitinoProvisionManager.GrantResult result = manager.grantDataPrivileges(request);

    Assertions.assertEquals(1, result.commandCount());
    Assertions.assertEquals(2, result.expandedCommandCount());

    Assertions.assertEquals(2, adapter.grants.size());
    Assertions.assertEquals("OneMeta", adapter.grants.get(0).metalake());
    Assertions.assertEquals("dr_t1_u1", adapter.grants.get(0).roleName());

    Assertions.assertEquals(
        MetadataObject.Type.COLUMN, adapter.grants.get(0).metadataObject().type());
    Assertions.assertEquals(
        "OneDS.sales.orders.amount", adapter.grants.get(0).metadataObject().fullName());
    Assertions.assertEquals(
        "OneDS.sales.orders.order_id", adapter.grants.get(1).metadataObject().fullName());
  }

  @Test
  public void testDuplicateColumnsShouldBeDeduplicated() throws IOException {
    FakeGravitinoClientAdapter adapter = new FakeGravitinoClientAdapter();
    GravitinoProvisionManager manager = new GravitinoProvisionManager(adapter);
    GrantDataPrivilegesRequest request =
        GrantDataPrivilegesRequest.newBuilder()
            .setTenantId(2)
            .addCommands(
                GrantDataPrivilegeCommand.newBuilder()
                    .setRoleName("dr_t2_u2")
                    .setObjectType(GravitinoObjectType.GRAVITINO_OBJECT_TYPE_COLUMN)
                    .setObjectName("OneDS.finance.payments")
                    .addColumnNames("amount")
                    .addColumnNames("amount")
                    .addColumnNames(" amount ")
                    .addPrivilegeNames("MODIFY_COLUMN")
                    .build())
            .build();

    GravitinoProvisionManager.GrantResult result = manager.grantDataPrivileges(request);

    Assertions.assertEquals(1, result.expandedCommandCount());
    Assertions.assertEquals(1, adapter.grants.size());
    Assertions.assertEquals("dr_t2_u2", adapter.grants.get(0).roleName());
  }

  @Test
  public void testInvalidPrivilegeNameShouldFail() {
    FakeGravitinoClientAdapter adapter = new FakeGravitinoClientAdapter();
    GravitinoProvisionManager manager = new GravitinoProvisionManager(adapter);
    GrantDataPrivilegesRequest request =
        GrantDataPrivilegesRequest.newBuilder()
            .setTenantId(3)
            .addCommands(
                GrantDataPrivilegeCommand.newBuilder()
                    .setRoleName("dr_t3_u3")
                    .setObjectType(GravitinoObjectType.GRAVITINO_OBJECT_TYPE_COLUMN)
                    .setObjectName("OneDS.ops.jobs")
                    .addColumnNames("status")
                    .addPrivilegeNames("UNKNOWN_PRIVILEGE")
                    .build())
            .build();

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> manager.grantDataPrivileges(request));
  }

  @Test
  public void testCrossGrainPrivilegeShouldFail() {
    FakeGravitinoClientAdapter adapter = new FakeGravitinoClientAdapter();
    GravitinoProvisionManager manager = new GravitinoProvisionManager(adapter);
    GrantDataPrivilegesRequest request =
        GrantDataPrivilegesRequest.newBuilder()
            .setTenantId(4)
            .addCommands(
                GrantDataPrivilegeCommand.newBuilder()
                    .setRoleName("dr_t4_u4")
                    .setObjectType(GravitinoObjectType.GRAVITINO_OBJECT_TYPE_TABLE)
                    .setObjectName("OneDS.ops.jobs")
                    .addPrivilegeNames("SELECT_COLUMN")
                    .build())
            .build();

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> manager.grantDataPrivileges(request));
  }

  private static final class FakeGravitinoClientAdapter extends GravitinoClientAdapter {
    private final List<GrantCall> grants = new ArrayList<>();

    private FakeGravitinoClientAdapter() {
      super(new GravitinoRpcProperties("http://127.0.0.1:8090", "OneMeta", "anonymous"));
    }

    @Override
    public void grantPrivilegesToRole(
        String metalake, String roleName, MetadataObject object, Set<Privilege> privileges) {
      grants.add(new GrantCall(metalake, roleName, object, privileges));
    }
  }

  private static final class GrantCall {
    private final String metalake;
    private final String roleName;
    private final MetadataObject metadataObject;
    private final Set<Privilege> privileges;

    private GrantCall(
        String metalake,
        String roleName,
        MetadataObject metadataObject,
        Set<Privilege> privileges) {
      this.metalake = metalake;
      this.roleName = roleName;
      this.metadataObject = metadataObject;
      this.privileges = privileges;
    }

    private String metalake() {
      return metalake;
    }

    private String roleName() {
      return roleName;
    }

    private MetadataObject metadataObject() {
      return metadataObject;
    }

    private Set<Privilege> privileges() {
      return privileges;
    }
  }
}
