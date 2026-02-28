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
package org.apache.gravitino.listener.api.event;

import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBaseEventTenantSnapshot {

  @Test
  public void testEventCapturesTenantSnapshot() {
    TenantContextHolder.set(
        TenantContext.builder()
            .withTenantId(11L)
            .withTenantCode("t11")
            .withTenantName("Tenant 11")
            .build());
    MockEvent event = new MockEvent("user", NameIdentifier.of("metalake", "catalog"));
    TenantContextHolder.remove();

    Assertions.assertEquals(11L, event.tenantId());
    Assertions.assertEquals("t11", event.tenantCode());
    Assertions.assertEquals("Tenant 11", event.tenantName());
  }

  @Test
  public void testEventWithoutTenantContext() {
    TenantContextHolder.remove();
    MockEvent event = new MockEvent("user", NameIdentifier.of("metalake", "catalog"));
    Assertions.assertNull(event.tenantId());
    Assertions.assertNull(event.tenantCode());
    Assertions.assertNull(event.tenantName());
  }

  private static class MockEvent extends Event {
    private MockEvent(String user, NameIdentifier identifier) {
      super(user, identifier);
    }
  }
}
