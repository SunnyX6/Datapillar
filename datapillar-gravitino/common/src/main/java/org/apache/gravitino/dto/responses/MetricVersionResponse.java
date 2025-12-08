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
package org.apache.gravitino.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.gravitino.dto.metric.MetricVersionDTO;

/** 指标版本响应 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class MetricVersionResponse extends BaseResponse {

  @JsonProperty("version")
  private final MetricVersionDTO version;

  public MetricVersionResponse(MetricVersionDTO version) {
    super(0);
    this.version = version;
  }

  /** Jackson 反序列化使用的默认构造函数 */
  public MetricVersionResponse() {
    super();
    this.version = null;
  }

  @Override
  public void validate() throws IllegalArgumentException {
    super.validate();
    Preconditions.checkArgument(version != null, "version must not be null");
  }
}
