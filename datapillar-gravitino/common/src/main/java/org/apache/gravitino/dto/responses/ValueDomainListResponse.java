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
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.dto.dataset.ValueDomainDTO;

/** Value field paginated list response */
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class ValueDomainListResponse extends BaseResponse {

  @JsonProperty("valueDomains")
  private final ValueDomainDTO[] valueDomains;

  @JsonProperty("total")
  private final long total;

  @JsonProperty("offset")
  private final int offset;

  @JsonProperty("limit")
  private final int limit;

  /**
   * Create a range list response
   *
   * @param valueDomains range array
   * @param total Total number of ranges
   * @param offset offset
   * @param limit page size
   */
  public ValueDomainListResponse(ValueDomainDTO[] valueDomains, long total, int offset, int limit) {
    super(0);
    this.valueDomains = valueDomains;
    this.total = total;
    this.offset = offset;
    this.limit = limit;
  }

  /** Jackson Default constructor for deserialization */
  public ValueDomainListResponse() {
    super();
    this.valueDomains = null;
    this.total = 0;
    this.offset = 0;
    this.limit = 0;
  }

  @Override
  public void validate() throws IllegalArgumentException {
    super.validate();

    Preconditions.checkArgument(valueDomains != null, "valueDomains must be non-null");
    Arrays.stream(valueDomains)
        .forEach(
            valueDomain -> {
              Preconditions.checkArgument(
                  StringUtils.isNotBlank(valueDomain.domainCode()),
                  "valueDomain 'domainCode' must not be null and empty");
            });
  }
}
