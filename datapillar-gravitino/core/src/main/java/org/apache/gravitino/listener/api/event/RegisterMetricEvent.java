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

/** Represents an event that is generated after a metric is successfully registered. */
@DeveloperApi
public class RegisterMetricEvent extends MetricEvent {
  private final MetricInfo registeredMetricInfo;

  /**
   * Constructs an instance of {@link RegisterMetricEvent}.
   *
   * @param user The user responsible for triggering the metric operation.
   * @param identifier The identifier of the Metric involved in the operation.
   * @param registeredMetricInfo The final state of the metric post-registration.
   */
  public RegisterMetricEvent(
      String user, NameIdentifier identifier, MetricInfo registeredMetricInfo) {
    super(user, identifier);
    this.registeredMetricInfo = registeredMetricInfo;
  }

  /**
   * Retrieves the final state of the metric after successful registration.
   *
   * @return The metric information.
   */
  public MetricInfo registeredMetricInfo() {
    return registeredMetricInfo;
  }

  @Override
  public OperationType operationType() {
    return OperationType.REGISTER_METRIC;
  }
}
