# Datapillar Auth / Gateway / Studio-Service 边界重构方案

## 1. 背景

Datapillar 之前的问题不是“小瑕疵”，而是职责本身就切错了：

1. `auth` 登录接口把 `menus` / `roles` 一起返回，直接越界到业务授权。
2. `gateway` 一边做入口认证，一边又直读 Redis 会话键，等于把 `auth` 的会话事实源偷了一半出来。
3. `studio-service` 已经拿到了网关注入的可信身份头，却还想继续重做外部身份解析。
4. token 密钥配置一会儿在 `auth`，一会儿在 `gateway`，事实源分裂。
5. `traceId` 在请求头、日志、错误体之间没有严格同值传递，导致报错时排查体验很差。

这套实现的本质问题只有一句话：

**认证、会话、入口鉴权、业务授权，被多个层同时做了。**

---

## 2. 本文目标

本文只做一件事：

**把 `auth center`、`api gateway`、`studio-service` 三层边界彻底切干净，并把认证链路收敛成单一事实源。**

本文不讨论：

1. 前端视觉交互。
2. 第三方 SSO 厂商接入细节。
3. 菜单/按钮/数据权限本身的产品规则设计。

---

## 3. 最终架构

Datapillar 采用如下目标模型：

```text
Client
  -> API Gateway
     -> local JWT verification by JWKS
     -> Auth session/context lookup
     -> Studio Service / AI / OpenLineage
```

这里的关键不是“gateway 调不调 auth”这种口水话，而是：

1. **JWT 的密码学合法性由 gateway 本地完成。**
2. **在线会话、用户状态、租户状态、角色上下文由 auth 作为唯一权威返回。**
3. **业务菜单与业务授权结果由 studio-service 唯一输出。**

一句话定义：

- `auth` 回答：**你是谁，这个会话现在还算不算活着。**
- `gateway` 回答：**这个请求能不能进入业务域。**
- `studio-service` 回答：**你进来以后到底能看什么、能做什么。**

---

## 4. 强制设计原则

1. **认证、会话、入口鉴权、业务授权必须分层。**
2. **同一种事实只能有一个最终权威来源。**
3. **登录响应禁止返回菜单树和业务授权结果。**
4. **gateway 禁止持有 auth token 公钥本地文件。**
5. **gateway 禁止直读 auth 的 Redis 会话键。**
6. **业务服务禁止再次解析外部 JWT。**
7. **业务服务禁止再次做 `iss/sub -> user` 身份重建。**
8. **所有失败响应必须统一为 `ErrorResponse`。**
9. **`traceId` 必须在请求头、日志、错误体之间保持同值。**
10. **token claim、auth 会话态、refresh 旋转必须使用同一份 `sid/jti` 事实。**

---

## 5. 单一事实源（Single Source of Truth）

| 事实 | 唯一权威 | 其他层允许做什么 | 禁止做什么 |
| --- | --- | --- | --- |
| token 私钥/公钥事实 | `auth.token.keyset-path` | `gateway` 只拉 JWKS | 本地再配 `public-key-path` |
| JWT 公钥发布 | `auth /.well-known/jwks.json` | `gateway` 拉取并缓存 | 自己维护第二份公钥 |
| JWT 密码学验签 | `gateway` | 本地验签、验 `iss/aud/exp/token_type` | 下游服务重复验外部 JWT |
| 在线会话活性 | `auth` | `gateway` 调 auth 获取结论 | `gateway` 直读 Redis 键自己判断 |
| 当前请求认证上下文 | `auth` | `gateway` 消费后注入可信头 | 业务服务重新构造 |
| 当前请求可信身份头 | `gateway` | `studio-service` 只消费 | 客户端自传/下游重建 |
| 菜单树 | `studio-service` | 前端消费 | `auth` 返回菜单 |
| 业务功能/对象授权 | `studio-service` | 前端消费 | `auth` / `gateway` 生成业务授权结果 |
| traceId | `gateway` | `auth` / `studio` 透传并写日志 | 各层各生成各的 |

---

## 6. 密钥与配置模型

### 6.1 Auth 端密钥配置

`auth` 只能保留一套 token 密钥配置：

```yaml
auth:
  token:
    issuer: https://auth.datapillar.local
    audience: datapillar-api
    keyset-path: classpath:security/auth-token-dev-keyset.json
```

规则如下：

1. `keyset-path` 是唯一的 token 密钥事实源。
2. keyset 文档内同时包含：
   - `activeKid`
   - `keys[]`
   - 每个 key 为 Ed25519 私有 JWK：`kid/kty/crv/alg/use/x/d`
3. `auth` 从同一份 keyset 中：
   - 读取 active 私钥签名 token
   - 派生 public-only JWKS 对外发布
4. 禁止再出现：
   - `private-key-path`
   - `public-key-path`
   - `active-kid` 外挂配置
   - token 签名算法的重复配置源

### 6.2 Gateway 端认证配置

`gateway` 只能保留认证策略配置，不允许保留 token 密钥文件配置：

```yaml
security:
  authentication:
    issuer: https://auth.datapillar.local
    audience: datapillar-api
    jwks-cache-seconds: 300
```

规则如下：

1. `gateway` 只认 `issuer`。
2. `gateway` 通过 `issuer` 推导：
   - `/.well-known/jwks.json`
   - `/auth/session/context`
3. 禁止再出现：
   - `public-key-path`
   - 本地 token 公钥文件
   - 与 auth 不一致的第二份算法配置

---

## 7. 标准请求链路

### 7.1 登录链路

```text
Client
  -> POST /api/auth/session/login
  -> Gateway route only
  -> Auth authenticate + issue cookies
  -> Auth return minimal login payload
  -> Client login success
  -> Client call /api/studio/biz/users/me/menu
```

登录成功响应只允许返回认证阶段需要的数据，例如：

1. `loginStage`
2. `userId`
3. `username`
4. `email`
5. `tenants`

禁止返回：

1. `menus`
2. `roles` 的业务解释结果
3. 页面可见性树
4. 任何业务对象授权结果

### 7.2 刷新链路

```text
Client
  -> POST /api/auth/session/refresh
  -> Gateway route only
  -> Auth validate refresh token
  -> Auth rotate refresh jti / access jti
  -> Auth rewrite cookies
  -> return success
```

refresh 仍然只属于会话生命周期，绝不顺带返回菜单。

### 7.3 业务请求链路

```text
Client
  -> GET /api/studio/**
  -> Gateway extract token
  -> Gateway local verify by auth JWKS
  -> Gateway call GET {issuer}/auth/session/context
  -> Auth return authoritative context
  -> Gateway inject trusted identity headers
  -> Studio trust headers and execute business authorization
```

这里必须明确：

1. `gateway` 本地完成 JWT 验签和标准 claim 校验。
2. `gateway` 再调用 `auth` 获取当前 token 的权威认证上下文。
3. `gateway` 不再直读 Redis。
4. `studio-service` 不再解析外部 JWT。

### 7.4 菜单链路

```text
Client
  -> GET /api/studio/biz/users/me/menu
  -> Gateway authenticate request
  -> Studio load menu tree for current user
  -> return menu payload
```

菜单接口必须是菜单的唯一出口。

---

## 8. 模块职责划分

### 8.1 Auth Center 职责

`auth` 只负责：

1. 用户登录。
2. SSO 接入。
3. access token / refresh token 签发。
4. refresh / logout / revoke。
5. JWKS / OpenID metadata。
6. 当前 token 的权威认证上下文解析：`GET /auth/session/context`。
7. 会话活性、用户状态、租户状态、租户成员状态的最终校验。

`auth` 禁止负责：

1. 菜单树。
2. 页面权限判定。
3. 按钮权限结果。
4. 数据对象授权结果。

### 8.2 API Gateway 职责

`gateway` 只负责：

1. 统一入口与路由。
2. token 提取（cookie / bearer）。
3. 从 auth 拉 JWKS 并本地缓存。
4. 本地 JWT 验签与标准 claim 校验。
5. 调 auth 获取权威认证上下文。
6. 拒绝客户端伪造可信身份头。
7. 注入可信身份头。
8. 统一错误响应。
9. 统一 `traceId` / `requestId`。

`gateway` 禁止负责：

1. 本地持有 token 公钥文件。
2. 直读 auth Redis 会话键。
3. 菜单树生成。
4. 业务授权解释。
5. 把 auth 的会话逻辑复制一份到自己模块里。

### 8.3 Studio Service 职责

`studio-service` 只负责：

1. 当前用户菜单。
2. 当前用户功能权限。
3. 当前用户对象级授权。
4. 多租户业务规则。
5. 消费可信身份头并构建本地业务上下文。

`studio-service` 禁止负责：

1. 签发 token。
2. refresh。
3. 解析外部 bearer token。
4. 再次做外部身份映射。
5. 再次验证外部 JWT 签名。

---

## 9. 权威认证上下文接口

`auth` 提供：

```text
GET /auth/session/context
Authorization: Bearer <access-token>
X-Trace-Id: <gateway-trace-id>
```

返回：

```json
{
  "code": 0,
  "data": {
    "userId": 101,
    "tenantId": 1001,
    "tenantCode": "t-1001",
    "tenantName": "tenant-1001",
    "username": "sunny",
    "email": "sunny@datapillar.ai",
    "roles": ["admin", "developer"],
    "impersonation": true,
    "actorUserId": 1,
    "actorTenantId": 0,
    "sessionId": "sid-1",
    "tokenId": "jti-1"
  }
}
```

规则如下：

1. `gateway` 调这个接口时必须透传同一个 `X-Trace-Id`。
2. `auth` 用这次调用返回权威结论。
3. `gateway` 只接受 `code = 0` 且 `data != null` 的返回。
4. `gateway` 必须校验 `sessionId/tokenId` 与本地已验签 token 一致。
5. `auth` 返回 `401/403` 时，`gateway` 必须继续输出统一 `ErrorResponse`。

---

## 10. 统一身份上下文契约

`gateway` 注入下游的可信身份头固定为：

1. `X-Principal-Iss`
2. `X-Principal-Sub`
3. `X-User-Id`
4. `X-Tenant-Id`
5. `X-Tenant-Code`
6. `X-Username`
7. `X-Email`
8. `X-User-Roles`
9. `X-Impersonation`
10. `X-Actor-User-Id`
11. `X-Actor-Tenant-Id`
12. `X-Trace-Id`
13. `X-Request-Id`

下游服务必须遵守：

1. 只信任这组头。
2. 不接受客户端自传同名头。
3. 不再解析外部 JWT。
4. 不再重建外部身份映射。

---

## 11. 统一错误与追踪契约

### 11.1 响应结构

Datapillar 统一采用：

- 成功：`ApiResponse`
- 失败：`ErrorResponse`

标准失败结构固定为：

```json
{
  "code": 401,
  "type": "UNAUTHORIZED",
  "message": "Invalid token",
  "traceId": "1772871641054-c1d0dae9"
}
```

禁止：

1. 有的接口错误返回 `ApiResponse`，有的返回裸 JSON。
2. 有的错误带 `traceId`，有的没有。
3. gateway 包一套错误结构，auth 再包另一套。

### 11.2 traceId 规则

`traceId` 必须满足：

1. 由 `gateway` 统一生成或接入。
2. 写入外部请求头。
3. 写入外部响应头。
4. 写入 gateway 日志。
5. `gateway` 调 `auth/session/context` 时继续透传。
6. 写入最终错误响应体。

这六处必须是同一个值。

### 11.3 WebFlux 约束

由于 `gateway` 是 reactive 模型，不能把 MDC 当唯一事实源。

必须遵守：

1. 错误体优先从 `ServerWebExchange` / request header 取 `traceId`。
2. MDC 只是日志桥接，不得充当错误体唯一来源。
3. `gateway -> auth` 内部调用必须显式透传 `X-Trace-Id`。

---

## 12. 当前实现的最终落地要求

### 12.1 Auth 必做

1. 删除登录返回中的 `menus` / `roles` 业务结果。
2. `LoginFinalizer` 不再查菜单树。
3. `auth.token.keyset-path` 成为唯一 token 密钥配置。
4. `/.well-known/jwks.json` 从同一份 keyset 派生公钥。
5. 提供 `GET /auth/session/context`，输出权威认证上下文。
6. `sid/access_jti/refresh_jti` 生成后必须原样写入 JWT 与会话态。
7. refresh 时的新旧 jti 旋转必须和会话态严格一致。

### 12.2 Gateway 必做

1. 删除本地 `public-key-path` 配置。
2. 启动时预热 auth JWKS，失败直接启动失败。
3. 请求时本地验签：`alg/iss/aud/exp/token_type/sub/sid/jti`。
4. 请求时调用 `GET {issuer}/auth/session/context` 获取权威上下文。
5. 禁止直读 Redis 判断 session active。
6. 认证成功后统一注入可信头。
7. 清洗客户端伪造的可信头。
8. 所有错误统一输出 `ErrorResponse`。
9. `gateway -> auth` 内部调用必须透传 `X-Trace-Id`。

### 12.3 Studio-Service 必做

1. 菜单树继续由 `studio-service` 唯一输出。
2. `TrustedIdentityFilter` 改成只消费可信头，不再做外部身份解析。
3. 菜单、功能、对象授权全部收敛在 `studio-service` 内部。

### 12.4 前端必做

1. 登录只视为“拿到会话”。
2. 菜单必须单独请求业务菜单接口。
3. 401/403 错误要读取并展示 `traceId`。
4. 前端不得同时依赖登录响应菜单和菜单接口两套来源。

---

## 13. 测试门禁

### 13.1 Auth 测试

至少覆盖：

1. 登录响应不包含 `menus`。
2. 登录响应不包含 `roles` 业务结果。
3. `GET /auth/session/context` 能返回权威上下文。
4. access token 的 `jti` 与会话态当前 `access_jti` 一致。
5. refresh 后新的 `access_jti` / `refresh_jti` 与会话态一致。

### 13.2 Gateway 测试

至少覆盖：

1. 有效 token 能通过本地 JWKS 验签。
2. `kid` 缺失时会触发 JWKS 刷新。
3. 非 access token 直接拒绝，且不调用 auth 上下文接口。
4. 篡改签名直接拒绝，且不调用 auth 上下文接口。
5. auth 返回 `401/403` 时，gateway 输出统一 `ErrorResponse`。
6. `gateway -> auth` 内部调用会透传 `X-Trace-Id`。
7. 客户端自传可信头会被拒绝。

### 13.3 Studio 测试

至少覆盖：

1. 菜单接口只依赖可信头上下文。
2. 菜单接口不需要解析外部 token。
3. 当前用户菜单来源唯一。

---

## 14. 强制禁止事项

以下行为必须明确禁止：

1. 在 `auth` 中继续维护菜单树返回逻辑。
2. 在 `gateway` 中继续保留 `public-key-path`。
3. 在 `gateway` 中继续直读 auth Redis 会话键。
4. 在 `studio-service` 中继续解析外部 JWT。
5. 在多个地方维护不同版本的 token 密钥配置。
6. 在 token claim 与会话态中维护不同版本的 `jti`。
7. 让客户端自传可信身份头进入业务服务。
8. 让不同层生成不同 `traceId`。

---

## 15. 最终结论

Datapillar 的认证边界必须收敛成下面这套模型：

1. `auth` 只做身份、会话、密钥事实源、权威认证上下文。
2. `gateway` 只做入口认证、JWKS 本地验签、向 auth 取权威上下文、注入可信头。
3. `studio-service` 只做业务授权和菜单输出。

这套方案解决的是结构性问题，不是表面修补：

1. 清掉 `auth` 的菜单越界。
2. 清掉 `gateway` 对 Redis 会话键的重复实现。
3. 清掉 token 公钥配置分裂。
4. 清掉业务服务对外部 token 的重复解析。
5. 把 `traceId`、错误结构、认证上下文都收敛成单一路径。

这才叫职责清晰。
