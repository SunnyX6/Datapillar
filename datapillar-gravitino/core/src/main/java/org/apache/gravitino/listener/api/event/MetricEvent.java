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

/**
 * Represents an abstract base class for events related to Metric operations. This class extends
 * {@link Event} to provide a more specific context involving operations on Metrics.
 */
@DeveloperApi
public abstract class MetricEvent extends Event {

  /**
   * Constructs a new {@link MetricEvent} with the specified user and metric identifier.
   *
   * @param user The user responsible for triggering the metric operation.
   * @param identifier The identifier of the Metric involved in the operation.
   */
  protected MetricEvent(String user, NameIdentifier identifier) {
    super(user, identifier);
  }

  @Override
  public OperationStatus operationStatus() {
    return OperationStatus.SUCCESS;
  }
}
