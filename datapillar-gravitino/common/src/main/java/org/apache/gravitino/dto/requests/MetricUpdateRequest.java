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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.rest.RESTRequest;

/** 更新指标的请求 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = MetricUpdateRequest.RenameMetricRequest.class, name = "rename"),
  @JsonSubTypes.Type(
      value = MetricUpdateRequest.RemoveMetricPropertyRequest.class,
      name = "removeProperty"),
  @JsonSubTypes.Type(
      value = MetricUpdateRequest.SetMetricPropertyRequest.class,
      name = "setProperty"),
  @JsonSubTypes.Type(
      value = MetricUpdateRequest.UpdateMetricCommentRequest.class,
      name = "updateComment"),
  @JsonSubTypes.Type(
      value = MetricUpdateRequest.UpdateMetricDataTypeRequest.class,
      name = "updateDataType")
})
public interface MetricUpdateRequest extends RESTRequest {

  /**
   * 返回指标变更
   *
   * @return 指标变更对象
   */
  MetricChange metricChange();

  /** 重命名指标的更新请求 */
  @EqualsAndHashCode
  @ToString
  class RenameMetricRequest implements MetricUpdateRequest {

    @Getter
    @JsonProperty("newName")
    private final String newName;

    @Override
    public MetricChange metricChange() {
      return MetricChange.rename(newName);
    }

    public RenameMetricRequest(String newName) {
      this.newName = newName;
    }

    public RenameMetricRequest() {
      this(null);
    }

    @Override
    public void validate() throws IllegalArgumentException {
      Preconditions.checkArgument(
          StringUtils.isNotBlank(newName), "\"newName\" field is required and cannot be empty");
    }
  }

  /** 设置指标属性的更新请求 */
  @EqualsAndHashCode
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @ToString
  @Getter
  class SetMetricPropertyRequest implements MetricUpdateRequest {
    @JsonProperty("property")
    private final String property;

    @JsonProperty("value")
    private final String value;

    @Override
    public MetricChange metricChange() {
      return MetricChange.setProperty(property, value);
    }

    @Override
    public void validate() throws IllegalArgumentException {
      Preconditions.checkArgument(
          StringUtils.isNotBlank(property), "\"property\" field is required and cannot be empty");
      Preconditions.checkArgument(value != null, "\"value\" field is required and cannot be null");
    }
  }

  /** 移除指标属性的更新请求 */
  @EqualsAndHashCode
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @ToString
  @Getter
  class RemoveMetricPropertyRequest implements MetricUpdateRequest {

    @JsonProperty("property")
    private final String property;

    @Override
    public MetricChange metricChange() {
      return MetricChange.removeProperty(property);
    }

    @Override
    public void validate() throws IllegalArgumentException {
      Preconditions.checkArgument(
          StringUtils.isNotBlank(property), "\"property\" field is required and cannot be empty");
    }
  }

  /** 更新指标注释的更新请求 */
  @EqualsAndHashCode
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @ToString
  @Getter
  class UpdateMetricCommentRequest implements MetricUpdateRequest {

    @JsonProperty("newComment")
    private final String newComment;

    @Override
    public MetricChange metricChange() {
      return MetricChange.updateComment(newComment);
    }

    @Override
    public void validate() throws IllegalArgumentException {
      Preconditions.checkArgument(
          newComment != null, "\"newComment\" field is required and cannot be null");
    }
  }

  /** 更新指标数据类型的更新请求 */
  @EqualsAndHashCode
  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @ToString
  @Getter
  class UpdateMetricDataTypeRequest implements MetricUpdateRequest {

    @JsonProperty("newDataType")
    private final String newDataType;

    @Override
    public MetricChange metricChange() {
      return MetricChange.updateDataType(newDataType);
    }

    @Override
    public void validate() throws IllegalArgumentException {
      Preconditions.checkArgument(
          StringUtils.isNotBlank(newDataType),
          "\"newDataType\" field is required and cannot be empty");
    }
  }
}
