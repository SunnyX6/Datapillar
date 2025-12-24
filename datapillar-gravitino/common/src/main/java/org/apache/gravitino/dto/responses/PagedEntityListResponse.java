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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.json.JsonUtils.NameIdentifierDeserializer;
import org.apache.gravitino.json.JsonUtils.NameIdentifierSerializer;

/** 分页实体列表响应 */
@EqualsAndHashCode(callSuper = true)
@ToString
public class PagedEntityListResponse extends BaseResponse {

  @JsonSerialize(contentUsing = NameIdentifierSerializer.class)
  @JsonDeserialize(contentUsing = NameIdentifierDeserializer.class)
  @JsonProperty("identifiers")
  private final NameIdentifier[] idents;

  @JsonProperty("total")
  private final long total;

  @JsonProperty("offset")
  private final int offset;

  @JsonProperty("limit")
  private final int limit;

  /**
   * 构造分页响应
   *
   * @param idents 实体标识符数组
   * @param total 总数
   * @param offset 偏移量
   * @param limit 每页大小
   */
  public PagedEntityListResponse(NameIdentifier[] idents, long total, int offset, int limit) {
    super(0);
    this.idents = idents;
    this.total = total;
    this.offset = offset;
    this.limit = limit;
  }

  /** 默认构造函数（用于 Jackson 反序列化） */
  public PagedEntityListResponse() {
    super();
    this.idents = null;
    this.total = 0;
    this.offset = 0;
    this.limit = 0;
  }

  public NameIdentifier[] identifiers() {
    return idents;
  }

  public long total() {
    return total;
  }

  public int offset() {
    return offset;
  }

  public int limit() {
    return limit;
  }

  @Override
  public void validate() throws IllegalArgumentException {
    super.validate();
    Preconditions.checkArgument(idents != null, "identifiers must not be null");
  }
}
