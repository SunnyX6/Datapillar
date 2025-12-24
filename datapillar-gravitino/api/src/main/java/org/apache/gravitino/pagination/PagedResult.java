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
package org.apache.gravitino.pagination;

import java.util.List;

/**
 * 分页结果封装类
 *
 * @param <T> 数据类型
 */
public class PagedResult<T> {

  /** 默认每页大小 */
  public static final int DEFAULT_LIMIT = 20;

  private final List<T> items;
  private final long total;
  private final int offset;
  private final int limit;

  public PagedResult(List<T> items, long total, int offset, int limit) {
    this.items = items;
    this.total = total;
    this.offset = offset;
    this.limit = limit;
  }

  public List<T> items() {
    return items;
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

  public boolean hasMore() {
    return offset + items.size() < total;
  }
}
