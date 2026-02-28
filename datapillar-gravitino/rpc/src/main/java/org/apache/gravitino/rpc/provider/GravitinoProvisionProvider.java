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
package org.apache.gravitino.rpc.provider;

import com.sunny.datapillar.common.rpc.security.v1.Error;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesRequest;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesResponse;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesResult;
import com.sunny.datapillar.common.rpc.security.v1.GravitinoProvisionService;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserRequest;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserResponse;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserResult;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.gravitino.rpc.service.GravitinoProvisionManager;

/** Dubbo provider implementation for Datapillar Gravitino RPC service. */
@DubboService(
    interfaceClass = GravitinoProvisionService.class,
    group = "${datapillar.rpc.group:datapillar}",
    version = "${datapillar.rpc.version:1.0.0}")
public class GravitinoProvisionProvider implements GravitinoProvisionService {

  private static final int CODE_BAD_REQUEST = 400;
  private static final int CODE_INTERNAL_ERROR = 500;
  private static final int CODE_SERVICE_UNAVAILABLE = 503;

  private static final String TYPE_REQUEST_INVALID = "GRAVITINO_RPC_REQUEST_INVALID";
  private static final String TYPE_PRIVILEGE_INVALID = "GRAVITINO_RPC_PRIVILEGE_INVALID";
  private static final String TYPE_OBJECT_INVALID = "GRAVITINO_RPC_OBJECT_INVALID";
  private static final String TYPE_UNAVAILABLE = "GRAVITINO_RPC_UNAVAILABLE";
  private static final String TYPE_INTERNAL_ERROR = "GRAVITINO_RPC_INTERNAL_ERROR";

  private final GravitinoProvisionManager provisionManager;

  public GravitinoProvisionProvider() {
    this(new GravitinoProvisionManager());
  }

  GravitinoProvisionProvider(GravitinoProvisionManager provisionManager) {
    this.provisionManager = provisionManager;
  }

  @Override
  public ProvisionUserResult provisionUser(ProvisionUserRequest request) {
    try {
      GravitinoProvisionManager.ProvisionResult result = provisionManager.provisionUser(request);
      ProvisionUserResponse data =
          ProvisionUserResponse.newBuilder()
              .setSuccess(true)
              .setPrincipal(result.principal())
              .setRoleName(result.roleName())
              .setTenantId(result.tenantId())
              .setUserId(result.userId())
              .build();
      return ProvisionUserResult.newBuilder().setData(data).build();
    } catch (Exception exception) {
      return ProvisionUserResult.newBuilder().setError(toRpcError(exception)).build();
    }
  }

  @Override
  public CompletableFuture<ProvisionUserResult> provisionUserAsync(ProvisionUserRequest request) {
    return CompletableFuture.completedFuture(provisionUser(request));
  }

  @Override
  public GrantDataPrivilegesResult grantDataPrivileges(GrantDataPrivilegesRequest request) {
    try {
      GravitinoProvisionManager.GrantResult result = provisionManager.grantDataPrivileges(request);
      GrantDataPrivilegesResponse data =
          GrantDataPrivilegesResponse.newBuilder()
              .setSuccess(true)
              .setCommandCount(result.commandCount())
              .setExpandedCommandCount(result.expandedCommandCount())
              .build();
      return GrantDataPrivilegesResult.newBuilder().setData(data).build();
    } catch (Exception exception) {
      return GrantDataPrivilegesResult.newBuilder().setError(toRpcError(exception)).build();
    }
  }

  @Override
  public CompletableFuture<GrantDataPrivilegesResult> grantDataPrivilegesAsync(
      GrantDataPrivilegesRequest request) {
    return CompletableFuture.completedFuture(grantDataPrivileges(request));
  }

  private Error toRpcError(Exception exception) {
    int code = CODE_INTERNAL_ERROR;
    String type = TYPE_INTERNAL_ERROR;
    boolean retryable = false;

    if (exception instanceof IllegalArgumentException) {
      code = CODE_BAD_REQUEST;
      type = resolveValidationErrorType(exception.getMessage());
    } else if (exception instanceof IOException) {
      code = CODE_SERVICE_UNAVAILABLE;
      type = TYPE_UNAVAILABLE;
      retryable = true;
    }

    String message = StringUtils.defaultIfBlank(exception.getMessage(), "服务器内部错误");
    return Error.newBuilder()
        .setCode(code)
        .setType(type)
        .setMessage(message)
        .setRetryable(retryable)
        .build();
  }

  private String resolveValidationErrorType(String message) {
    if (StringUtils.containsIgnoreCase(message, "privilege")) {
      return TYPE_PRIVILEGE_INVALID;
    }
    if (StringUtils.containsIgnoreCase(message, "object_type")
        || StringUtils.containsIgnoreCase(message, "column_names")
        || StringUtils.containsIgnoreCase(message, "object_name")) {
      return TYPE_OBJECT_INVALID;
    }
    return TYPE_REQUEST_INVALID;
  }
}
