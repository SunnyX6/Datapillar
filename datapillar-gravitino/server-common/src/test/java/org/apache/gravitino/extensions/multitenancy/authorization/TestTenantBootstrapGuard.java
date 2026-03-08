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
package org.apache.gravitino.extensions.multitenancy.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.gravitino.UserPrincipal;
import org.apache.gravitino.multitenancy.context.ExternalUserIdContextHolder;
import org.apache.gravitino.multitenancy.context.TenantContext;
import org.apache.gravitino.multitenancy.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TestTenantBootstrapGuard {

  @AfterEach
  public void clearContext() {
    ExternalUserIdContextHolder.remove();
    TenantContextHolder.remove();
  }

  @Test
  public void testCanCreateMetalake() {
    assertFalse(TenantBootstrapGuard.canCreateMetalake(new UserPrincipal("sunny")));

    TenantContextHolder.set(
        TenantContext.builder().withTenantId(1L).withTenantCode("tenant-1").build());
    assertFalse(TenantBootstrapGuard.canCreateMetalake(new UserPrincipal("sunny")));

    ExternalUserIdContextHolder.set("1001");
    assertTrue(TenantBootstrapGuard.canCreateMetalake(new UserPrincipal("sunny")));
  }
}
