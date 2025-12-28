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
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.listener.api.info.MetricInfo;

/** Represents an event that is generated after a metric is successfully altered. */
@DeveloperApi
public class AlterMetricEvent extends MetricEvent {
  private final MetricInfo updatedMetricInfo;

  /**
   * Constructs an instance of {@link AlterMetricEvent}.
   *
   * @param user The user responsible for triggering the metric operation.
   * @param identifier The identifier of the Metric involved in the operation.
   * @param updatedMetricInfo The final state of the metric post-alteration.
   */
  public AlterMetricEvent(String user, NameIdentifier identifier, MetricInfo updatedMetricInfo) {
    super(user, identifier);
    this.updatedMetricInfo = updatedMetricInfo;
  }

  /**
   * Retrieves the final state of the metric after successful alteration.
   *
   * @return The metric information.
   */
  public MetricInfo updatedMetricInfo() {
    return updatedMetricInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.ALTER_METRIC;
  }
}
