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
package org.apache.gravitino.listener.openlineage.converters;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.RunEvent;
import java.net.URI;
import java.util.Collections;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBaseEventConverter {

  @Test
  public void testTenantAwareNamespaceMapping() {
    try {
      TenantContextHolder.set(
          TenantContext.builder()
              .withTenantId(7L)
              .withTenantCode("t7")
              .withTenantName("T7")
              .build());
      MockEvent event =
          new MockEvent("user", NameIdentifier.of("metalake", "catalog", "schema", "table"));
      TestConverter converter = new TestConverter();

      String datasetNamespace = converter.datasetNamespace(event, event.identifier());
      RunEvent runEvent = converter.toRunEvent(event);

      Assertions.assertEquals("gravitino://tenant/7/metalake/catalog", datasetNamespace);
      Assertions.assertEquals("gravitino://tenant/7", runEvent.getJob().getNamespace());
    } finally {
      TenantContextHolder.remove();
    }
  }

  @Test
  public void testRejectEventWithoutTenantId() {
    TenantContextHolder.remove();
    MockEvent event =
        new MockEvent("user", NameIdentifier.of("metalake", "catalog", "schema", "table"));
    TestConverter converter = new TestConverter();

    IllegalArgumentException exception =
        Assertions.assertThrows(IllegalArgumentException.class, () -> converter.toRunEvent(event));
    Assertions.assertTrue(exception.getMessage().contains("missing tenant_id"));
  }

  @Test
  public void testTenantFacetIncludesTenantSnapshot() {
    try {
      TenantContextHolder.set(
          TenantContext.builder()
              .withTenantId(9L)
              .withTenantCode("t9")
              .withTenantName("Tenant-9")
              .build());
      MockEvent event =
          new MockEvent("user", NameIdentifier.of("metalake", "catalog", "schema", "table"));
      TestConverter converter = new TestConverter();

      GravitinoDatasetFacet facet = converter.toTenantFacet(event);
      Assertions.assertEquals(9L, facet.getTenantId());
      Assertions.assertEquals("t9", facet.getTenantCode());
      Assertions.assertEquals("Tenant-9", facet.getTenantName());
    } finally {
      TenantContextHolder.remove();
    }
  }

  private static class TestConverter extends BaseEventConverter {
    private TestConverter() {
      super(new OpenLineage(URI.create("https://github.com/apache/gravitino")), "gravitino");
    }

    private String datasetNamespace(Event event, NameIdentifier identifier) {
      return formatDatasetNamespace(event, identifier);
    }

    private RunEvent toRunEvent(Event event) {
      return createRunEvent(
          event,
          "test.job",
          OpenLineage.RunEvent.EventType.COMPLETE,
          Collections.emptyList(),
          Collections.emptyList());
    }

    private GravitinoDatasetFacet toTenantFacet(Event event) {
      return tenantFacetBuilder(event).build();
    }
  }

  private static class MockEvent extends Event {
    private MockEvent(String user, NameIdentifier identifier) {
      super(user, identifier);
    }
  }
}
