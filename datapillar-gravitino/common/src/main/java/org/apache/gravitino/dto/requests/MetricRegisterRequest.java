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
import com.google.common.base.Preconditions;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.rest.RESTRequest;

/** 表示注册指标的请求 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MetricRegisterRequest implements RESTRequest {

  @JsonProperty("name")
  private String name;

  @JsonProperty("code")
  private String code;

  @JsonProperty("type")
  private Metric.Type type;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("properties")
  private Map<String, String> properties;

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
    Preconditions.checkArgument(
        StringUtils.isNotBlank(name), "\"name\" field is required and cannot be empty");
    Preconditions.checkArgument(
        StringUtils.isNotBlank(code), "\"code\" field is required and cannot be empty");
    Preconditions.checkArgument(type != null, "\"type\" field is required and cannot be null");

    // DERIVED 和 COMPOSITE 类型必须指定 parentMetricIds
    if (type == Metric.Type.DERIVED || type == Metric.Type.COMPOSITE) {
      Preconditions.checkArgument(
          parentMetricIds != null && parentMetricIds.length > 0,
          "\"parentMetricIds\" is required for %s metric type",
          type.name());
    }
  }
}
