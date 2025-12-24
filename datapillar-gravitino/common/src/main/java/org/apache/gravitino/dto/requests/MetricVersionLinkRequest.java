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
package org.apache.gravitino.dto.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.gravitino.rest.RESTRequest;

/** 创建新指标版本的请求（版本号自动递增） */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MetricVersionLinkRequest implements RESTRequest {

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("unit")
  private String unit;

  @JsonProperty("aggregationLogic")
  private String aggregationLogic;

  @JsonProperty("parentMetricIds")
  private Long[] parentMetricIds;

  @JsonProperty("calculationFormula")
  private String calculationFormula;

  @Override
  public void validate() throws IllegalArgumentException {
    // 所有字段都是可选的
  }
}
