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
import org.apache.gravitino.dto.dataset.ValueDomainDTO;

/** 值域响应 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class ValueDomainResponse extends BaseResponse {

  @JsonProperty("valueDomain")
  private ValueDomainDTO valueDomain;

  /** 无参构造函数，用于 Jackson 反序列化 */
  public ValueDomainResponse() {
    super(0);
  }

  /**
   * 创建 ValueDomainResponse
   *
   * @param valueDomain 值域 DTO
   */
  public ValueDomainResponse(ValueDomainDTO valueDomain) {
    super(0);
    this.valueDomain = valueDomain;
  }
}
