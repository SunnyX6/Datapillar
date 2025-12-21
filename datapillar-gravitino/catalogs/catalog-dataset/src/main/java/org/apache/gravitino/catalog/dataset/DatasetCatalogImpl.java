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
package org.apache.gravitino.catalog.dataset;

import java.util.Map;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.EntityStore;
import org.apache.gravitino.GravitinoEnv;
import org.apache.gravitino.connector.BaseCatalog;
import org.apache.gravitino.connector.CatalogOperations;
import org.apache.gravitino.connector.PropertiesMetadata;
import org.apache.gravitino.connector.capability.Capability;

/** Dataset Catalog 实现类 */
public class DatasetCatalogImpl extends BaseCatalog<DatasetCatalogImpl> {

  private static final DatasetCatalogPropertiesMetadata CATALOG_PROPERTIES_META =
      new DatasetCatalogPropertiesMetadata();

  private static final DatasetSchemaPropertiesMetadata SCHEMA_PROPERTIES_META =
      new DatasetSchemaPropertiesMetadata();

  private static final MetricPropertiesMetadata METRIC_PROPERTIES_META =
      new MetricPropertiesMetadata();

  private static final MetricVersionPropertiesMetadata METRIC_VERSION_PROPERTIES_META =
      new MetricVersionPropertiesMetadata();

  @Override
  public String shortName() {
    return "dataset";
  }

  @Override
  public Catalog.Type catalogType() {
    return Catalog.Type.DATASET;
  }

  @Override
  protected CatalogOperations newOps(Map<String, String> config) {
    EntityStore store = GravitinoEnv.getInstance().entityStore();
    return new DatasetCatalogOperations(store);
  }

  @Override
  public PropertiesMetadata catalogPropertiesMetadata() throws UnsupportedOperationException {
    return CATALOG_PROPERTIES_META;
  }

  @Override
  public PropertiesMetadata schemaPropertiesMetadata() throws UnsupportedOperationException {
    return SCHEMA_PROPERTIES_META;
  }

  @Override
  public PropertiesMetadata metricPropertiesMetadata() throws UnsupportedOperationException {
    return METRIC_PROPERTIES_META;
  }

  @Override
  public PropertiesMetadata metricVersionPropertiesMetadata() throws UnsupportedOperationException {
    return METRIC_VERSION_PROPERTIES_META;
  }

  @Override
  protected Capability newCapability() {
    return new DatasetCatalogCapability();
  }
}
