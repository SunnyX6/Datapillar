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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.gravitino.dto.dataset.UnitDTO;

/** 单位响应 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class UnitResponse extends BaseResponse {

  @JsonProperty("unit")
  private UnitDTO unit;

  /** 无参构造函数，用于 Jackson 反序列化 */
  public UnitResponse() {
    super(0);
  }

  /**
   * 创建 UnitResponse
   *
   * @param unit 单位 DTO
   */
  public UnitResponse(UnitDTO unit) {
    super(0);
    this.unit = unit;
  }
}
