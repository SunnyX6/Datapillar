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
package org.apache.gravitino.storage.relational.bootstrap;

import java.util.ServiceLoader;
import org.apache.gravitino.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelationalBackendBootstrapLoader {
  private static final Logger LOG = LoggerFactory.getLogger(RelationalBackendBootstrapLoader.class);

  private RelationalBackendBootstrapLoader() {}

  public static void runBeforeSqlSessionFactoryInit(Config config) {
    ServiceLoader<RelationalBackendBootstrap> serviceLoader =
        ServiceLoader.load(RelationalBackendBootstrap.class);
    for (RelationalBackendBootstrap bootstrap : serviceLoader) {
      LOG.info("Applying relational backend bootstrap: {}", bootstrap.getClass().getName());
      bootstrap.beforeSqlSessionFactoryInit(config);
    }
  }
}
