# Datapillar Auth 单体系架构设计（Greenfield）

## 1. 背景与目标

本设计按新项目重构口径执行，不考虑兼容，不设计迁移，不保留双轨。

目标只有一个：彻底消灭“双体系并存”的认证垃圾逻辑，建立唯一可信认证体系。

标准链路固定为：

```text
Client
  -> datapillar-auth (Authenticator SPI: simple | oauth2)
  -> issue JWT (Ed25519 + kid)
  -> API Gateway (issuer/jwks 验签 + claim 校验 + trusted headers 注入)
  -> studio/ai/openlineage (只信网关注入身份上下文)
```

---

## 2. 强制设计原则

1. 认证权威唯一：只有 `datapillar-auth` 可以签发业务访问令牌。
2. 令牌合同唯一：所有模式统一同一套 JWT claim 规范。
3. 验签入口唯一：网关仅信任 `datapillar-auth` 的 `issuer` 与 `jwks`。
4. 模式切换是认证器切换，不是体系切换：`simple` 与 `oauth2` 共享同一发 token 能力。
5. `auth.authenticator` 必须是单值（`simple` 或 `oauth2`），禁止多值并存。
6. 认证与授权解耦：auth 负责“你是谁”，业务服务负责“你能干什么”。
7. 运行时无回源鉴权：网关本地验签，不对 auth 做同步 validate RPC。

---

## 3. 必须删除的旧逻辑

1. 删除网关对 Keycloak 的依赖配置与实现路径。
2. 删除 auth 内 HMAC 作为业务访问令牌签名算法的实现。
3. 删除 claim 命名冲突与双规范（`userId/tenantId` vs `user_id/tenant_id`）并存。
4. 删除“外部 issuer 验签 + 内部 issuer 发 token”并行运行逻辑。
5. 删除 auth 内与认证中心职责无关的能力耦合（例如租户密钥加解密 RPC）。

---

## 4. 认证架构（参考 Gravitino 思想）

### 4.1 Authenticator SPI

认证器接口只负责“身份认证结果”，不负责 token 最终格式。

```java
public interface Authenticator {
  String name();
  AuthenticationResult authenticate(AuthenticationRequest request);
  default void initialize(AuthConfig config) {}
}
```

内置实现：

1. `SimpleAuthenticator`：本地账号密码或开发态最简身份认证。
2. `OAuth2Authenticator`：标准 OAuth2/OIDC 登录认证，支持外部提供方。

### 4.2 统一 Token Engine

`TokenEngine` 是唯一签发能力入口，所有认证器最终都调用它签发同一合同 token。

职责：

1. 访问令牌签发（Access Token）。
2. 刷新令牌签发与轮换（Refresh Token Rotation）。
3. `sid/jti` 会话状态管理与撤销。
4. 统一 claim 装配与签名。

### 4.3 OAuth Token Validator SPI（oauth2 模式内部）

参考 Gravitino 的 `TokenValidator` 插件模式，oauth2 认证器支持可替换校验器：

1. `JwksTokenValidator`：通过提供方 JWKS 验证第三方身份令牌。
2. `StaticKeyTokenValidator`：通过静态公钥验证。

注意：这里是“认证输入令牌校验器”，不是业务访问令牌签发器。业务访问令牌仍由本系统签发。

认证器启用规则：

1. 运行时只允许启用一个认证器。
2. `auth.authenticator=simple` 时启用 `SimpleAuthenticator`。
3. `auth.authenticator=oauth2` 时启用 `OAuth2Authenticator`。
4. 禁止链式执行多个认证器。

---

## 5. 令牌与密钥标准

### 5.1 签名算法

统一使用 `EdDSA(Ed25519)`，禁止 HMAC 业务访问令牌。

### 5.2 Key 管理

KeyManager 必须支持：

1. 活跃签名 key（active `kid`）。
2. 多版本公钥发布（JWKS）。
3. 密钥轮转（新签发用新 `kid`，旧 token 仍可验）。

### 5.3 JWKS 与 Discovery

auth 必须提供：

1. `GET /.well-known/jwks.json`
2. `GET /.well-known/openid-configuration`

Discovery 至少包含：

1. `issuer`
2. `jwks_uri`
3. `token_endpoint`
4. `token_endpoint` 固定为 `POST /oauth2/token`（与文档接口定义保持一致）

---

## 6. 统一 Claim 合同（固定，不允许分叉）

### 6.1 标准字段

1. `iss`
2. `sub`
3. `aud`
4. `exp`
5. `iat`
6. `nbf`
7. `jti`

### 6.2 业务字段（统一 snake_case）

1. `sid`
2. `user_id`
3. `tenant_id`
4. `tenant_code`
5. `tenant_codes`
6. `preferred_username`
7. `email`
8. `roles`
9. `impersonation`
10. `actor_user_id`
11. `actor_tenant_id`
12. `token_type` (`access` / `refresh`)

### 6.3 租户字段语义（强制）

1. `tenant_id` 与 `tenant_code` 表示“当前会话租户”，访问控制只以这两个字段为准。
2. `tenant_codes` 仅用于前端展示“可切换租户列表”，不得作为请求期鉴权依据。
3. 请求期如需切换租户，必须重新走 auth 签发新 token，禁止客户端靠 header 指定租户。

禁止：

1. 同时发 `userId` 与 `user_id`。
2. 同时发 `tenantId` 与 `tenant_id`。
3. 不带 `aud` 的访问令牌。

---

## 7. 对外接口设计（新口径）

### 7.1 会话接口

1. `POST /auth/session/login`（simple）
2. `POST /auth/session/oauth2/login`（oauth2 code exchange）
3. `POST /auth/session/refresh`
4. `POST /auth/session/logout`
5. `GET /auth/session/me`
6. `GET /auth/session/oauth2/authorize`（签发 `state/nonce`，返回授权跳转参数）

### 7.2 标准元数据接口

1. `GET /.well-known/openid-configuration`
2. `GET /.well-known/jwks.json`

### 7.3 OAuth2 协议接口

1. `POST /oauth2/token`（与 discovery `token_endpoint` 对齐）
2. `grant_type` 至少支持：`authorization_code`、`refresh_token`
3. oauth2 登录必须校验：`state`、`nonce`、`PKCE(code_verifier)`

### 7.4 健康检查

1. `GET /auth/health`

---

## 8. 网关架构约束（硬约束）

1. 网关只从 `issuer-uri` 或 `jwk-set-uri` 校验 auth 签发 token。
2. 网关本地校验 `iss/aud/exp/nbf/signature`，不回源 auth。
3. 网关必须先清理客户端传入的同名身份头，再注入可信头。
4. 网关只允许注入以下可信头：`X-Principal-Iss`、`X-Principal-Sub`、`X-Tenant-Id`、`X-Tenant-Code`、`X-User-Id`、`X-Username`、`X-User-Email`、`X-User-Roles`。
5. `X-Tenant-Id` 与 `X-Tenant-Code` 只能来自 token 的当前租户字段，禁止采信客户端自带租户头。
6. 下游服务禁止自行解析 Bearer token，只读取网关注入头。
7. 网关禁止保留 Keycloak 专有配置项。

### 8.1 租户上下文传递规则（防遗漏）

1. 客户端传入的 `X-Tenant-Id`、`X-Tenant-Code` 一律视为不可信输入，网关必须忽略；在严格模式下可直接拒绝请求。
2. 网关只能从访问令牌的 `tenant_id`、`tenant_code` 提取当前租户，并重建可信头。
3. 网关注入的 `X-Tenant-Id`、`X-Tenant-Code` 是下游唯一可用租户上下文来源。
4. `studio-service` 只能从 `TrustedIdentityFilter`（或等价安全上下文）读取租户与用户身份，禁止回退读取客户端原始 header。
5. `datapillar-ai` 只能消费网关注入的可信身份头；若缺少可信头或来源不可信，必须失败返回（fail closed）。
6. `openlineage` 只能消费网关注入的可信身份头；若缺少可信头或来源不可信，必须失败返回（fail closed）。
7. 租户切换必须通过重新签发 token 完成，禁止客户端或下游服务通过改写 header 直接切租户。

---

## 9. simple / oauth2 两种模式定义

### 9.1 simple 模式（快速验证产品）

1. 使用本地用户体系认证。
2. 认证通过后签发标准 Ed25519 JWT。
3. 整个链路与 oauth2 模式共享同一 token 合同与 jwks。

### 9.2 oauth2 模式（标准认证）

1. 使用 OAuth2/OIDC 登录流程完成身份认证。
2. 外部令牌仅用于身份确认，不直接作为业务访问令牌。
3. 认证完成后仍由本系统签发标准 Ed25519 JWT。

结论：模式切换只影响“身份认证方式”，不影响“业务访问令牌标准”；且 `auth.authenticator` 只能单值配置。

---

## 10. 模块边界（单服务结构）

```text
Datapillar/
  datapillar-common/                  # 现有公共模块，继续复用

  datapillar-auth/                    # 单服务，不拆子项目
    src/main/java/com/sunny/datapillar/auth/
      config/                         # 配置与装配
        AuthProperties.java
        SecurityConfig.java
        WebMvcSecurityConfig.java
      api/                            # HTTP 入口（session/well-known/health）
        session/
          SessionController.java
          OAuth2TokenController.java
        wellknown/
          WellKnownController.java
        health/
          HealthController.java
      service/                        # 应用编排服务层（供 Controller 调用）
        AuthAppService.java
        SessionAppService.java
        TokenAppService.java
      authentication/                 # 认证器 SPI 与实现
        Authenticator.java
        AuthenticationRequest.java
        AuthenticationResult.java
        simple/
          SimpleAuthenticator.java
        oauth2/
          OAuth2Authenticator.java
        validator/
          OAuthTokenValidator.java
          JwksTokenValidator.java
          StaticKeyTokenValidator.java
      token/                          # Token 能力层（签发/校验/claim）
        TokenEngine.java
        JwtTokenEngine.java
        ClaimAssembler.java
        TokenClaims.java
        TokenIssuer.java
        TokenVerifier.java
      key/                            # Ed25519 密钥管理与 JWKS 发布
        KeyManager.java
        KeyRotationService.java
        JwksPublisher.java
      session/                        # sid/jti 会话状态与 refresh 轮换
        SessionStore.java
        RedisSessionStore.java
        SessionState.java
      filter/                         # Web 过滤器
        TraceIdFilter.java
      security/                       # 安全拦截器与令牌能力
        AuthCsrfInterceptor.java
        JwtToken.java
      mapper/                         # MyBatis mapper 接口
        UserMapper.java
        TenantMapper.java
        TenantUserMapper.java
      entity/                         # 持久化实体
        User.java
        Tenant.java
        TenantUser.java
      dto/                            # 请求/响应 DTO
        auth/
        login/
        oauth/
      util/                           # 工具类
        ClientIpUtil.java
        HashUtil.java
      error/                          # 统一错误模型
        AuthErrorCode.java
        AuthException.java
        AuthExceptionHandler.java

    src/main/resources/
      application.yml
      mapper/*.xml
```

边界约束：

1. 公共常量、通用安全工具统一放在 `datapillar-common`，auth 不重复造轮子。
2. `api` 只负责协议编排与参数校验，不承载核心认证逻辑。
3. `service` 只编排用例，不实现签发/验签等底层能力。
4. 核心认证逻辑统一收敛在 `authentication/token/key/session` 四类目录。
5. 禁止新增第二套签发器或第二套 token claim 规范。

### 10.1 必要文件清单（必须存在）

1. `api/session/SessionController.java`：登录、刷新、登出、当前会话查询入口。
2. `api/session/OAuth2TokenController.java`：`/oauth2/token` 协议端点。
3. `api/wellknown/WellKnownController.java`：`/.well-known/openid-configuration` 与 `/.well-known/jwks.json`。
4. `authentication/Authenticator.java`：认证器统一 SPI。
5. `authentication/simple/SimpleAuthenticator.java`：simple 模式实现。
6. `authentication/oauth2/OAuth2Authenticator.java`：oauth2 模式实现。
7. `authentication/validator/JwksTokenValidator.java`：外部 OAuth token JWKS 校验器。
8. `token/JwtTokenEngine.java`：唯一业务 token 签发与校验实现。
9. `token/ClaimAssembler.java`：统一 claim 组装，避免命名分叉。
10. `key/KeyManager.java`：活动 `kid` 管理与签名密钥装载。
11. `key/JwksPublisher.java`：JWKS 发布。
12. `session/SessionStore.java`：会话状态抽象。
13. `session/RedisSessionStore.java`：`sid/jti` 存储与 refresh 轮换。
14. `error/AuthExceptionHandler.java`：统一异常到 HTTP 响应映射。
15. `config/AuthProperties.java`：认证配置统一入口。

### 10.2 Controller 与 Service 对应关系（强制）

1. `api/session/SessionController.java` -> `service/SessionAppService.java`
2. `api/session/OAuth2TokenController.java` -> `service/SessionAppService.java`
3. `api/wellknown/WellKnownController.java` -> `service/TokenAppService.java`
4. `api/health/HealthController.java` -> `service/AuthAppService.java`

约束：

1. `SessionController`、`WellKnownController`、`HealthController` 只能调用 `service/*AppService`。
2. `OAuth2TokenController` 作为协议端点，允许读取 `TokenEngine` 与 `AuthProperties` 组装 RFC 响应字段，但认证流程必须委托给 `SessionAppService`。
3. `*AppService` 再调用 `token/TokenEngine`、`session/SessionStore`、`authentication/Authenticator` 等能力接口。

### 10.3 Token 能力调用边界（强制）

1. `TokenEngine` 是 token 能力唯一入口，`service/*AppService` 只能依赖 `TokenEngine`。
2. `TokenIssuer` 与 `TokenVerifier` 是 `TokenEngine` 内部协作组件，不允许被 Controller 或 AppService 直接依赖。
3. `JwtTokenEngine` 是 `TokenEngine` 的唯一实现类，禁止并存第二个生产实现。
4. `ClaimAssembler` 只能由 `TokenEngine` 调用，禁止在其他层手工拼 claims。

---

## 11. 配置模型（统一）

```yaml
auth:
  authenticator: simple # enum: simple | oauth2（单值）
  token:
    issuer: https://auth.datapillar.local
    audience: datapillar-api
    algorithm: EdDSA
    access-ttl-seconds: 3600
    refresh-ttl-seconds: 604800
  jwks:
    enabled: true
    active-kid: auth-2026-01
  oauth2:
    provider: generic
    authority: https://idp.example.com
    jwks-uri: https://idp.example.com/.well-known/jwks.json
    principal-fields: preferred_username,email,sub
```

---

## 12. 禁止事项（红线）

1. 禁止再引入“外部 issuer 验签 + 内部 issuer 发 token”双体系。
2. 禁止在网关保留 Keycloak 专属逻辑分支。
3. 禁止 auth 同时维护多套 claim 命名规范。
4. 禁止用 HMAC 继续签发业务访问令牌。
5. 禁止下游服务自行解析客户端 Bearer token。
6. 禁止把与认证无关的密钥加解密业务继续耦合到 auth 核心链路。
7. 禁止将 `auth.authenticator` 配置为多值或链式执行。

---

## 13. 验收标准

1. 关闭任何外部 IdP 时，`simple` 模式可独立完成全链路登录访问。
2. 启用 `oauth2` 模式时，外部身份认证成功后仍由 auth 统一签发访问令牌。
3. 网关只需 auth 的 `issuer/jwks` 即可完成验签与身份头注入。
4. studio/ai/openlineage 无 token 解析代码路径。
5. 配置层不存在 Keycloak 字段与双体系开关。
6. `auth.authenticator` 为单值且运行时仅加载一个认证器。

---

## 14. 实施 TODO 清单（可打钩）

### 14.1 基线治理（先删垃圾再开发）

- [x] 删除网关 Keycloak 相关配置项、自动装配、条件分支与文档说明。
- [x] 删除 auth 中 HMAC 业务访问令牌签发逻辑与相关配置项。
- [x] 删除 claim 双命名并存逻辑（`userId/tenantId`）与兼容分支。
- [x] 删除“外部 issuer 验签 + 内部 issuer 发 token”并行路径。
- [x] 删除 auth 内与认证中心职责无关的密钥加解密 RPC 耦合能力。

### 14.2 项目结构落地（按第 10 节目录）

- [x] 建立 `config/api/service/authentication/token/key/session/filter/mapper/entity/dto/util/error` 目录骨架。
- [x] 新增 `api/session/SessionController.java` 并仅依赖 `SessionAppService`。
- [x] 新增 `api/session/OAuth2TokenController.java` 并按 OAuth2 协议暴露 `/oauth2/token`。
- [x] 新增 `api/wellknown/WellKnownController.java` 并仅依赖 `TokenAppService`。
- [x] 新增 `api/health/HealthController.java` 并仅依赖 `AuthAppService`。
- [x] 新增 `service/AuthAppService.java`、`service/SessionAppService.java`、`service/TokenAppService.java`。
- [x] 新增 `authentication/Authenticator.java`、`AuthenticationRequest.java`、`AuthenticationResult.java`。
- [x] 新增 `authentication/simple/SimpleAuthenticator.java`。
- [x] 新增 `authentication/oauth2/OAuth2Authenticator.java`。
- [x] 新增 `authentication/validator/OAuthTokenValidator.java`、`JwksTokenValidator.java`、`StaticKeyTokenValidator.java`。
- [x] 新增 `token/TokenEngine.java`、`JwtTokenEngine.java`、`ClaimAssembler.java`、`TokenClaims.java`、`TokenIssuer.java`、`TokenVerifier.java`。
- [x] 新增 `key/KeyManager.java`、`KeyRotationService.java`、`JwksPublisher.java`。
- [x] 新增 `session/SessionStore.java`、`RedisSessionStore.java`、`SessionState.java`。
- [x] 新增 `error/AuthErrorCode.java`、`AuthException.java`、`AuthExceptionHandler.java`。

### 14.3 配置与装配（单值认证器）

- [x] 在 `AuthProperties` 中定义 `auth.authenticator` 单值枚举：`simple` 或 `oauth2`。
- [x] 启动时校验 `auth.authenticator` 非法值直接 fail fast。
- [x] 启动时仅装配一个 `Authenticator`，禁止链式执行多个实现。
- [x] 配置 `auth.token.issuer/audience/algorithm/access-ttl-seconds/refresh-ttl-seconds`。
- [x] 配置 `auth.jwks.enabled/active-kid`。
- [x] 配置 `auth.oauth2.provider/authority/jwks-uri/principal-fields`。
- [x] 删除所有 Keycloak 专有配置字段与读取逻辑。

### 14.4 认证链路实现（simple + oauth2）

- [x] `SimpleAuthenticator` 完成本地账号密码认证并返回标准认证结果。
- [x] `OAuth2Authenticator` 实现授权码交换与身份确认流程。
- [x] oauth2 登录流程强制校验 `state`。
- [x] oauth2 登录流程强制校验 `nonce`。
- [x] oauth2 登录流程强制校验 PKCE（`code_verifier`）。
- [x] oauth2 认证输入令牌校验支持 `JwksTokenValidator`。
- [x] oauth2 认证输入令牌校验支持 `StaticKeyTokenValidator`。
- [x] 两种认证器认证成功后统一调用 `TokenEngine` 签发业务访问令牌。

### 14.5 Token 与 Claim 标准化

- [x] 业务访问令牌签名算法固定为 `EdDSA(Ed25519)`。
- [x] `ClaimAssembler` 统一组装标准字段：`iss/sub/aud/exp/iat/nbf/jti`。
- [x] `ClaimAssembler` 统一组装业务字段：`sid/user_id/tenant_id/tenant_code/tenant_codes/preferred_username/email/roles/impersonation/actor_user_id/actor_tenant_id/token_type`。
- [x] 禁止输出 `userId`、`tenantId` 等 camelCase claim。
- [x] 强制访问令牌包含 `aud`。
- [x] `token_type` 严格限定为 `access` 或 `refresh`。

### 14.6 会话与刷新令牌

- [x] 实现 `sid/jti` 会话状态存储模型。
- [x] 实现 refresh token rotation（旧 refresh 令牌作废）。
- [x] 实现 `logout` 后会话撤销与令牌失效。
- [x] `SessionStore` 抽象不暴露 Redis 细节给上层。
- [x] `RedisSessionStore` 加入 TTL 与并发更新保护。

### 14.7 Well-Known 与 OAuth2 协议端点

- [x] 提供 `GET /.well-known/jwks.json`。
- [x] 提供 `GET /.well-known/openid-configuration`。
- [x] discovery 中返回正确 `issuer`。
- [x] discovery 中返回正确 `jwks_uri`。
- [x] discovery 中返回正确 `token_endpoint`（`/oauth2/token`）。
- [x] 提供 `POST /oauth2/token` 并支持 `authorization_code`。
- [x] `POST /oauth2/token` 支持 `refresh_token`。

### 14.8 网关落地（信任边界）

- [x] 网关本地校验 `iss/aud/exp/nbf/signature`，不回源 auth。
- [x] 网关先清理客户端传入同名身份头后再注入可信头。
- [x] 网关仅注入白名单身份头：`X-Principal-Iss/X-Principal-Sub/X-Tenant-Id/X-Tenant-Code/X-User-Id/X-Username/X-User-Email/X-User-Roles`。
- [x] 网关从 token claim 提取 `tenant_id/tenant_code` 生成 `X-Tenant-Id/X-Tenant-Code`。
- [x] 网关忽略或拒绝客户端自带 `X-Tenant-Id/X-Tenant-Code`（严格模式可拒绝）。
- [x] 网关删除 Keycloak 分支与遗留条件判断。

### 14.9 下游服务改造（studio / ai / openlineage）

- [x] `studio-service` 仅从 `TrustedIdentityFilter`（或等价上下文）读取身份信息。
- [x] `studio-service` 删除解析客户端 Bearer token 的代码路径。
- [x] `studio-service` 禁止回退读取客户端原始租户头。
- [x] `datapillar-ai` 仅消费网关注入的可信身份头。
- [x] `datapillar-ai` 缺失可信身份头时 fail closed。
- [x] `openlineage` 服务侧删除直接 token 解析路径。
- [x] `openlineage` 的鉴权过滤器必须要求 `X-Principal-Iss/X-Principal-Sub/X-Tenant-Id/X-Tenant-Code/X-User-Id` 全量存在。
- [x] `openlineage` 的租户解析必须以可信头为准，事件 facet 只能做一致性校验，不得作为租户归属决策来源。
- [x] `openlineage` 对租户不一致场景返回明确 4xx（禁止静默回退）。

### 14.10 测试清单（必须全绿）

- [x] 单元测试覆盖 `SimpleAuthenticator` 成功/失败路径。
- [x] 单元测试覆盖 `OAuth2Authenticator` 成功/失败路径。
- [x] 单元测试覆盖 `ClaimAssembler` 字段完整性与命名规范。
- [x] 单元测试覆盖 `JwtTokenEngine` 签发、验签、过期与伪造场景。
- [x] 单元测试覆盖 `SessionStore` refresh 轮换与并发更新。
- [x] 接口级测试覆盖 `/.well-known/jwks.json` 与 discovery 返回正确性。
- [x] 接口级测试覆盖 `/oauth2/token` 两种 grant_type。
- [x] 集成测试覆盖网关“清理客户端头 + 注入可信头”链路。
- [x] 集成测试覆盖客户端伪造租户头被忽略/拒绝。
- [x] 模块级链路测试覆盖 `simple` 登录访问关键路径（认证器、签发、网关可信头注入、下游可信头消费）。
- [x] 模块级链路测试覆盖 `oauth2` 登录访问关键路径（state/nonce/pkce、`/oauth2/token`、可信头链路）。

### 14.11 交付门禁（合并前必须满足）

- [x] `auth.authenticator` 在运行时可观测且仅一个实现生效。
- [x] 仓库内无 Keycloak 配置残留、无 HMAC 访问令牌签发残留。
- [x] 下游服务无直接 Bearer token 解析残留代码。
- [x] `spotless`、编译、测试全部通过。
- [x] 验收标准 13.1-13.6 全部满足并有测试证据。
