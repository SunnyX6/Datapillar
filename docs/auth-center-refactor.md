# 认证中心改造文档

## 改造目标

- 认证体系采用“集中认证、网关统一认证执行”：
  - Auth：唯一认证中心（Token/Session 校验与主体解析）。
  - Gateway：唯一认证执行点（通过 Dubbo 调 Auth）。
  - 业务服务（Java/Python）：零认证决策，只做业务授权与业务逻辑。
- 不做兼容改造，直接切换新链路。
- common 只承载 Dubbo 协议标准，生产者与消费者遵循同一协议。

## 完整流程（原封不动）

不是不用 JWT。是不用“每个业务服务自己验 JWT”。

正确模式（你要的“集中认证、非分布式认证执行”）
- Auth：唯一认证中心。
- Gateway：唯一认证执行点（通过 Dubbo 调 Auth）。
- 业务服务（Java/Python）：零认证决策，只做业务授权与业务逻辑。

完整流程

1) 登录
- 客户端 -> Gateway -> Auth（登录接口）
- Auth 颁发：
  - `access JWT`（短时效，含 `sid/jti/sub`）
  - `refresh token`（长时效，轮转）
- 会话状态在 Auth 的 Redis/DB 维护（可撤销、可踢出）。

2) 访问业务 API
- 客户端带 `Authorization: Bearer <access JWT>` 请求 Gateway。
- Gateway 先清洗客户端伪造身份头（`X-User-Id/X-Tenant-Id/...`）。
- Gateway 通过 **Dubbo** 调 Auth：`CheckAuthentication(token, method, path, tenantHint)`。
- Auth 做认证判定：签名、过期、sid/jti、租户状态、用户状态。
- Auth 返回 `authenticated/deny + principal(userId, tenantId, roles) + gateway_assertion(按资源路由 audience)`。
- Gateway 允许才转发给业务服务，并注入内部可信上下文（建议带网关签名或 mTLS 约束）。

3) 刷新
- 客户端 -> Gateway -> Auth `/refresh`
- Auth 校验 refresh，轮转 refresh+jti，返回新 access/refresh。

4) 登出/撤销
- 客户端 -> Gateway -> Auth `/logout|revoke`
- Auth 标记 sid/jti 失效；后续请求 Gateway 调 Auth 都会被拒绝。

Dubbo 在这里怎么用
- **必须用在 Gateway <-> Auth 的认证判定通信**（控制面）。
- Gateway -> 业务服务可继续 HTTP（当前网关是 Spring Cloud Gateway 的 HTTP 路由模型），也可改 Dubbo（含 Python provider/consumer），但那是数据面改造，不影响“集中认证”核心。

## 微服务架构图

### 组件架构图

```text
                         控制平面（Control Plane）
                Dubbo Triple + Proto 标准协议
+-------------------+  <-------------------------->  +----------------------+
| API Gateway (PEP) |                                | Auth Service (AuthN) |
| - 统一入口         |                                | - login/refresh/revoke|
| - 清洗伪造头       |                                | - CheckAuthentication 认证判定 |
| - 注入网关断言头    |                                | - 签发 gateway_assertion|
| - 执行放行/拒绝    |                                | - 会话状态(sid/jti)   |
+---------+---------+                                +----------+-----------+
          |                                                     ^
          | HTTP 转发 + 注入可信主体上下文                     |
          v                                                     |
+-------------------+   +-------------------+   +-------------------+
| Studio Service    |   | AI Service        |   | Other Services    |
| - 不验JWT         |   | - 不验JWT         |   | - 不验JWT         |
| - 做业务授权(RBAC/ABAC)| | - 做业务授权(RBAC/ABAC)| | - 做业务授权(RBAC/ABAC)|
+-------------------+   +-------------------+   +-------------------+

(外部只暴露 Gateway；业务服务内网化)
```

### 请求时序图

```text
[登录]
Client -> Gateway -> Auth(/login)
Auth -> Gateway: access JWT + refresh token
Gateway -> Client: token/cookie

[业务访问]
Client -> Gateway: Authorization: Bearer <access JWT>
Gateway -> Auth(Dubbo): CheckAuthentication(token, method, path, tenantHint)
Auth -> Gateway: authenticated/deny + principal + gateway_assertion
Gateway -> Service: 转发业务请求 + principal上下文 + gateway_assertion(可信)
Service -> Gateway -> Client: 业务响应

[刷新/撤销]
Client -> Gateway -> Auth(/refresh or /revoke)
Auth 更新 sid/jti 状态并返回结果
```

## 协议标准（common 统一治理）

- 协议版本：`datapillar.security.v1`
- 协议载体：Proto（IDL-first）+ Dubbo Triple
- 禁止：`GenericService + Map` 用于核心安全链路
- 命名规则：协议命名使用通用语义，不允许带调用方语义（例如禁止 `*Gateway*Service`）

### 协议目录

```text
datapillar-common/
  src/main/proto/datapillar/security/v1/
    common.proto
    authentication.proto
    crypto.proto
```

### common.proto（公共元信息与主体）

```proto
syntax = "proto3";
package datapillar.security.v1;
option java_multiple_files = true;
option java_package = "com.sunny.datapillar.common.rpc.security.v1";

message RpcMeta {
  string protocol_version = 1; // security.v1
  string caller_service = 2;
  string trace_id = 3;
  string request_id = 4;
  string client_ip = 5;
  map<string,string> attrs = 6;
}

message Principal {
  int64 user_id = 1;
  int64 tenant_id = 2;
  string username = 3;
  string email = 4;
  repeated string roles = 5;
  bool impersonation = 6;
  int64 actor_user_id = 7;
  int64 actor_tenant_id = 8;
  string sid = 9;
  string jti = 10;
}

enum DenyCode {
  DENY_CODE_UNSPECIFIED = 0;
  TOKEN_MISSING = 1;
  TOKEN_INVALID = 2;
  TOKEN_EXPIRED = 3;
  SESSION_REVOKED = 4;
  TENANT_DISABLED = 5;
  USER_DISABLED = 6;
  PERMISSION_DENIED = 7;
  SYSTEM_UNAVAILABLE = 8;
}
```

### authentication.proto（统一认证判定）

```proto
syntax = "proto3";
package datapillar.security.v1;
option java_multiple_files = true;
option java_package = "com.sunny.datapillar.common.rpc.security.v1";

message CheckAuthenticationRequest {
  RpcMeta meta = 1;
  string token = 2;
  string method = 3;
  string path = 4;
  int64 tenant_id_hint = 5;
}

message CheckAuthenticationResponse {
  bool authenticated = 1;
  DenyCode deny_code = 2;
  string message = 3;
  Principal principal = 4;
  string gateway_assertion = 5;
}

service AuthenticationService {
  rpc CheckAuthentication(CheckAuthenticationRequest) returns (CheckAuthenticationResponse);
}
```

### crypto.proto（通用加解密）

```proto
syntax = "proto3";
package datapillar.security.v1;
option java_multiple_files = true;
option java_package = "com.sunny.datapillar.common.rpc.security.v1";

message EncryptRequest { RpcMeta meta = 1; int64 tenant_id = 2; string purpose = 3; string plaintext = 4; }
message EncryptResponse { string ciphertext = 1; }
message DecryptRequest { RpcMeta meta = 1; int64 tenant_id = 2; string purpose = 3; string ciphertext = 4; }
message DecryptResponse { string plaintext = 1; }
message SavePrivateKeyRequest { RpcMeta meta = 1; int64 tenant_id = 2; string private_key_pem = 3; }
message SavePrivateKeyResponse { bool success = 1; }
message ExistsPrivateKeyRequest { RpcMeta meta = 1; int64 tenant_id = 2; }
message ExistsPrivateKeyResponse { bool exists = 1; }

service CryptoService {
  rpc Encrypt(EncryptRequest) returns (EncryptResponse);
  rpc Decrypt(DecryptRequest) returns (DecryptResponse);
  rpc SavePrivateKey(SavePrivateKeyRequest) returns (SavePrivateKeyResponse);
  rpc ExistsPrivateKey(ExistsPrivateKeyRequest) returns (ExistsPrivateKeyResponse);
}
```

## 目录结构（服务内实现）

```text
datapillar-auth/
  src/main/java/com/sunny/datapillar/auth/rpc/provider/security/
    AuthenticationProvider.java
    CryptoProvider.java
  src/main/java/com/sunny/datapillar/auth/security/
    SessionStateStore.java
    AuthAssertionSigner.java

datapillar-api-gateway/
  src/main/java/com/sunny/datapillar/gateway/
    DatapillarGatewayApplication.java
  src/main/java/com/sunny/datapillar/gateway/config/
    AuthenticationProperties.java
  src/main/java/com/sunny/datapillar/gateway/security/
    AuthenticationFilter.java
    ClientIpResolver.java
    SetupStateChecker.java

datapillar-studio-service/
  src/main/java/com/sunny/datapillar/studio/filter/
    GatewayAssertionFilter.java
  src/main/java/com/sunny/datapillar/studio/security/
    GatewayAssertionVerifier.java
    GatewayAssertionContext.java
  src/main/java/com/sunny/datapillar/studio/rpc/crypto/
    AuthCryptoRpcClient.java

datapillar-ai/
  src/shared/auth/
    gateway_assertion.py
    middleware.py
    user.py
  src/shared/auth/security/
    gateway-assertion-dev-public.pem
```

## 职责边界

### Auth

- 提供 HTTP：`/login`、`/refresh`、`/logout|revoke`
- 提供 Dubbo：`AuthenticationService`、`CryptoService`
- 认证判定链：签名 -> 过期 -> sid/jti -> 租户状态 -> 用户状态
- 仅做认证，不做 RBAC/ABAC 业务授权决策
- 返回标准化 `authenticated/deny + principal + deny_code + gateway_assertion`

### Gateway

- 所有受保护请求先调 `AuthenticationService.CheckAuthentication`
- `authenticated=false` 直接拒绝（401/403）
- `authenticated=true` 才转发业务请求
- 强制清洗客户端伪造身份头后再写内部上下文
- Dubbo 异常默认拒绝（fail-close）

### 业务服务（Studio/AI）

- 不解析 JWT
- 不访问 Auth 做认证决策
- 仅消费 Gateway 注入的可信主体上下文
- 仅做资源级业务授权与业务逻辑

## 配置与依赖调整

- Gateway 增加 Dubbo 依赖并启用 Dubbo。
- common 增加 proto 编译插件，生成统一 Java stub。
- common 删除手写 RPC 契约（`com.sunny.datapillar.common.rpc.security/**`），协议唯一真源为 `src/main/proto/datapillar/security/v1/**`。
- Auth 与 Gateway 统一 `datapillar.rpc.group`、`datapillar.rpc.version`。
- 删除/替换旧链路：
  - 网关转 Auth 代理转发业务：`config/nacos/dev/DATAPILLAR/datapillar-api-gateway.yaml:95`
  - auth 内部旧 RPC 契约定义：`datapillar-auth/src/main/java/com/sunny/datapillar/auth/rpc/crypto/AuthCryptoService.java:12`
  - studio GenericService 客户端：`datapillar-studio-service/src/main/java/com/sunny/datapillar/studio/rpc/crypto/AuthCryptoGenericClient.java:30`

## 安全约束

- 业务服务端口不对外暴露，只允许 Gateway 访问。
- Gateway 到业务服务传递主体信息必须可验证（签名或 mTLS 约束）。
- 统一 deny_code 与审计字段（trace_id/request_id/sid/jti）。

## 改造前现状证据（改造依据）

- Gateway 当前无 Dubbo 依赖：`datapillar-api-gateway/pom.xml:21`
- Auth/Studio 已启用 Dubbo：
  - `datapillar-auth/src/main/java/com/sunny/datapillar/auth/DatapillarAuthApplication.java:16`
  - `datapillar-studio-service/src/main/java/com/sunny/datapillar/studio/DatapillarStudioApplication.java:15`
- 当前业务流量经 Auth 代理转发：`config/nacos/dev/DATAPILLAR/datapillar-api-gateway.yaml:95`

## 改造落地结果（2026-02-19）

- RPC 契约已完成单一真源收敛：Gateway/Auth/Studio 全部切换到 `com.sunny.datapillar.common.rpc.security.v1.*`（proto 生成类），不再使用手写 `common.rpc.security` 接口与 DTO：
  - `datapillar-api-gateway/src/main/java/com/sunny/datapillar/gateway/security/AuthenticationFilter.java:1`
  - `datapillar-auth/src/main/java/com/sunny/datapillar/auth/rpc/provider/security/AuthenticationProvider.java:1`
  - `datapillar-auth/src/main/java/com/sunny/datapillar/auth/rpc/provider/security/CryptoProvider.java:1`
  - `datapillar-studio-service/src/main/java/com/sunny/datapillar/studio/rpc/crypto/AuthCryptoRpcClient.java:1`
  - 已删除手写契约目录：`datapillar-common/src/main/java/com/sunny/datapillar/common/rpc/security/`
- 网关已移除 SSE 的 `protected-service` / `/proxy` 回退语义，SSE 仅绑定 `ai-service` 路由：
  - `datapillar-api-gateway/src/main/java/com/sunny/datapillar/gateway/config/SseRouteConfig.java:1`
- Auth 的 `AuthenticationProvider` 已收敛为纯认证判定：只调用 `resolveAuthenticationContext`，不再在 Auth 内执行 RBAC/ABAC；并按资源前缀签发目标 audience 的网关断言：
  - `/api/studio/**` -> `datapillar-studio-service`
  - `/api/ai/**` -> `datapillar-ai`
  - `datapillar-auth/src/main/java/com/sunny/datapillar/auth/rpc/provider/security/AuthenticationProvider.java:1`
  - `datapillar-auth/src/main/java/com/sunny/datapillar/auth/security/AuthAssertionSigner.java:1`
- Auth 内部残留授权策略实现已清理，避免职责误导（Auth 只保留认证能力）：
  - 已移除 `datapillar-auth/src/main/java/com/sunny/datapillar/auth/security/policy/*`
  - 已移除 `datapillar-auth/src/test/java/com/sunny/datapillar/auth/security/policy/*`
- AI 服务已切换为只信任网关断言：中间件强制校验 `X-Gateway-Assertion`（签名/issuer/audience/method/path），不再信任外部注入的用户头：
  - `datapillar-ai/src/shared/auth/gateway_assertion.py:1`
  - `datapillar-ai/src/shared/auth/middleware.py:1`
  - `datapillar-ai/src/shared/config/runtime.py:1`
  - `config/nacos/dev/DATAPILLAR/datapillar-ai.yaml:1`
  - `config/nacos/prod/DATAPILLAR/datapillar-ai.yaml:1`
- Auth/Studio 旧测试中残留的 `common.error` / `BusinessException` 依赖已清理并全部切换到当前异常体系：
  - `datapillar-auth/src/test/java/com/sunny/datapillar/auth/response/ApiResponseTest.java:1`
  - `datapillar-auth/src/test/java/com/sunny/datapillar/auth/security/AuthCsrfInterceptorTest.java:1`
  - `datapillar-auth/src/test/java/com/sunny/datapillar/auth/service/AuthServiceTest.java:1`
  - `datapillar-auth/src/test/java/com/sunny/datapillar/auth/service/LoginServiceImplTest.java:1`
  - `datapillar-auth/src/test/java/com/sunny/datapillar/auth/service/login/method/sso/SsoStateStoreTest.java:1`
  - `datapillar-studio-service/src/test/java/com/sunny/datapillar/studio/module/llm/service/impl/LlmManagerServiceImplTest.java:1`
  - `datapillar-studio-service/src/test/java/com/sunny/datapillar/studio/module/setup/service/impl/SetupServiceImplTest.java:1`
  - `datapillar-studio-service/src/test/java/com/sunny/datapillar/studio/module/tenant/service/impl/InvitationServiceImplTest.java:1`
  - `datapillar-studio-service/src/test/java/com/sunny/datapillar/studio/module/tenant/service/impl/SsoConfigServiceImplTest.java:1`
  - `datapillar-studio-service/src/test/java/com/sunny/datapillar/studio/module/tenant/service/impl/SsoIdentityServiceImplTest.java:1`

### 实测命令与结果

- 命令：
  - `mvn -Dmaven.repo.local=/Users/sunny/Projects/Datapillar/.m2repo -pl datapillar-common,datapillar-auth,datapillar-api-gateway,datapillar-studio-service -am test -DskipITs`
  - `mvn -Dmaven.repo.local=/Users/sunny/Projects/Datapillar/.m2repo -pl datapillar-auth -am test -DskipITs`
  - `cd datapillar-ai && uv run --extra dev ruff check src/shared/auth/gateway_assertion.py src/shared/auth/middleware.py src/shared/config/runtime.py tests/test_gateway_assertion_auth.py tests/test_nacos_config.py`
  - `cd datapillar-ai && uv run --extra dev pytest tests/test_gateway_assertion_auth.py tests/test_nacos_config.py -q`
- 结果：
  - `datapillar-common`、`datapillar-auth`、`datapillar-studio-service`、`datapillar-api-gateway` 全量单测通过，构建成功。
  - `datapillar-auth` 单测通过（`BUILD SUCCESS`）。
  - `datapillar-ai` 相关检查通过（`12 passed`）。

## 历史故障链路复盘（/tmp/datapillar-logs）

- 历史主故障链路是旧的 `Gateway -> /proxy -> AuthProxyService -> 业务服务`，在 Auth 日志中可见：
  - `AuthProxyService.forward`、`AuthProxyController.proxy`
  - 典型错误为 `缺少认证信息`（旧代理链路中 token/上下文不稳定）
- 同时存在基础设施问题，不属于业务认证实现缺陷：
  - Nacos 客户端反复 `Client not connected / token expired / Read timed out`
  - 这会放大网关与服务发现异常，表现为链路时断时续
- 本次改造后已移除上述代理链路，认证链路收敛为：
  - `Gateway --Dubbo(CheckAuthentication)--> Auth`
  - `Gateway --HTTP(注入可信主体)--> 业务服务`
