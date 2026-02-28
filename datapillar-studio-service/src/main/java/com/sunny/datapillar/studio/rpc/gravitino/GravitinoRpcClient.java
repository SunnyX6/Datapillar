package com.sunny.datapillar.studio.rpc.gravitino;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegeCommand;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesRequest;
import com.sunny.datapillar.common.rpc.security.v1.GrantDataPrivilegesResult;
import com.sunny.datapillar.common.rpc.security.v1.GravitinoObjectType;
import com.sunny.datapillar.common.rpc.security.v1.GravitinoProvisionService;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserRequest;
import com.sunny.datapillar.common.rpc.security.v1.ProvisionUserResult;
import com.sunny.datapillar.common.rpc.security.v1.RpcMeta;
import java.util.List;
import java.util.Map;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Gravitino RPC轻量客户端，仅负责请求拼装与错误映射。 */
@Component
public class GravitinoRpcClient {

    private static final String PROTOCOL_VERSION = "security.v1";

    @Value("${spring.application.name:datapillar-studio-service}")
    private String caller;

    @Value("${datapillar.gravitino.metalake:OneMeta}")
    private String metalake;

    @DubboReference(
            interfaceClass = GravitinoProvisionService.class,
            version = "${datapillar.rpc.version:1.0.0}",
            group = "${datapillar.rpc.group:datapillar}"
    )
    private GravitinoProvisionService provisionService;

    public void provisionInvitationUser(Long tenantId,
                                        String tenantCode,
                                        Long userId,
                                        String username,
                                        List<String> roleNames) {
        if (tenantId == null || tenantId <= 0 || userId == null || userId <= 0) {
            throw new BadRequestException(ErrorType.GRAVITINO_RPC_REQUEST_INVALID, Map.of(), "参数错误");
        }

        ProvisionUserRequest.Builder builder = ProvisionUserRequest.newBuilder()
                .setMeta(buildMeta())
                .setTenantId(tenantId)
                .setUserId(userId)
                .setMetalake(resolveMetalake());
        if (StringUtils.hasText(tenantCode)) {
            builder.setTenantCode(tenantCode.trim());
        }
        if (StringUtils.hasText(username)) {
            builder.setUsername(username.trim());
        }
        if (roleNames != null) {
            roleNames.stream().filter(StringUtils::hasText).map(String::trim).forEach(builder::addRoleNames);
        }

        ProvisionUserResult result = invokeRpc(() -> provisionService.provisionUser(builder.build()));
        if (result == null) {
            throw new ServiceUnavailableException(ErrorType.GRAVITINO_RPC_UNAVAILABLE, Map.of(), "Gravitino RPC 不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData() || !result.getData().getSuccess()) {
            throw new InternalException(ErrorType.GRAVITINO_RPC_INTERNAL_ERROR, Map.of(), "Gravitino RPC 返回无效结果");
        }
    }

    public void grantDataPrivileges(Long tenantId,
                                    String tenantCode,
                                    List<GrantCommand> commands) {
        if (tenantId == null || tenantId <= 0 || commands == null || commands.isEmpty()) {
            throw new BadRequestException(ErrorType.GRAVITINO_RPC_REQUEST_INVALID, Map.of(), "参数错误");
        }

        GrantDataPrivilegesRequest.Builder requestBuilder = GrantDataPrivilegesRequest.newBuilder()
                .setMeta(buildMeta())
                .setTenantId(tenantId)
                .setMetalake(resolveMetalake());
        if (StringUtils.hasText(tenantCode)) {
            requestBuilder.setTenantCode(tenantCode.trim());
        }

        for (GrantCommand command : commands) {
            if (command == null) {
                continue;
            }
            GrantDataPrivilegeCommand.Builder commandBuilder = GrantDataPrivilegeCommand.newBuilder();
            if (StringUtils.hasText(command.roleName())) {
                commandBuilder.setRoleName(command.roleName().trim());
            }
            if (command.objectType() != null) {
                commandBuilder.setObjectType(command.objectType());
            }
            if (StringUtils.hasText(command.objectName())) {
                commandBuilder.setObjectName(command.objectName().trim());
            }
            if (command.columnNames() != null) {
                command.columnNames().stream().filter(StringUtils::hasText).map(String::trim).forEach(commandBuilder::addColumnNames);
            }
            if (command.privilegeNames() != null) {
                command.privilegeNames().stream().filter(StringUtils::hasText).map(String::trim).forEach(commandBuilder::addPrivilegeNames);
            }
            requestBuilder.addCommands(commandBuilder.build());
        }

        GrantDataPrivilegesResult result = invokeRpc(() -> provisionService.grantDataPrivileges(requestBuilder.build()));
        if (result == null) {
            throw new ServiceUnavailableException(ErrorType.GRAVITINO_RPC_UNAVAILABLE, Map.of(), "Gravitino RPC 不可用");
        }
        if (result.hasError()) {
            throw mapRpcError(result.getError());
        }
        if (!result.hasData() || !result.getData().getSuccess()) {
            throw new InternalException(ErrorType.GRAVITINO_RPC_INTERNAL_ERROR, Map.of(), "Gravitino RPC 返回无效结果");
        }
    }

    private RpcMeta buildMeta() {
        return RpcMeta.newBuilder()
                .setProtocolVersion(PROTOCOL_VERSION)
                .setCallerService(caller)
                .putAllAttrs(Map.of("caller", caller))
                .build();
    }

    private String resolveMetalake() {
        if (StringUtils.hasText(metalake)) {
            return metalake.trim();
        }
        return "OneMeta";
    }

    private <T> T invokeRpc(RpcInvoker<T> invoker) {
        try {
            return invoker.invoke();
        } catch (RpcException rpcException) {
            throw new ServiceUnavailableException(
                    rpcException,
                    ErrorType.GRAVITINO_RPC_UNAVAILABLE,
                    Map.of(),
                    "Gravitino RPC 不可用");
        }
    }

    private RuntimeException mapRpcError(com.sunny.datapillar.common.rpc.security.v1.Error rpcError) {
        String type = StringUtils.hasText(rpcError.getType())
                ? rpcError.getType()
                : ErrorType.GRAVITINO_RPC_INTERNAL_ERROR;
        String message = StringUtils.hasText(rpcError.getMessage()) ? rpcError.getMessage() : "Gravitino RPC 错误";
        Map<String, String> context = rpcError.getContextMap();

        return switch (rpcError.getCode()) {
            case 400 -> new BadRequestException(type, context, message);
            case 503, 502 -> new ServiceUnavailableException(type, context, message);
            default -> new InternalException(type, context, message);
        };
    }

    @FunctionalInterface
    private interface RpcInvoker<T> {
        T invoke();
    }

    public record GrantCommand(String roleName,
                               GravitinoObjectType objectType,
                               String objectName,
                               List<String> columnNames,
                               List<String> privilegeNames) {
    }
}
