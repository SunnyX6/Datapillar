# Datapillar 开放平台 API_KEY 认证方案

## 1. 背景

OpenLineage 只是触发点，不是方案边界。

真正要解决的问题是：

**Datapillar 作为 gateway 型平台，如何把“前端用户访问”与“开放平台程序化访问”设计成两种明确的认证方式，并收敛到同一套内部主体模型。**

这里必须先把几种错误思路砍掉：

1. 不能把 API key 设计成 OpenLineage 专用 token。
2. 不能让 API key 只作为一个“租户归属标签”，却没有主体语义。
3. 不能让 gateway 根据 credential 形态做回退尝试或猜测式判型。
4. 不能让下游服务自己解析原始 JWT 或原始 API key。
5. 不能让一个业务服务内部同时维护两套原始认证逻辑。
6. 不能用“隐藏用户”“伪装 service account user”来偷渡 API key。

本文定义的是**完整开放平台方案**，不是某个服务的局部补丁。

---

## 2. 目标

Datapillar 要同时满足下面四个目标：

1. 前端用户调用与开放平台调用是两种**显式认证方式**。
2. 用户和开放平台使用者都能清楚知道自己应该使用哪种认证方式。
3. gateway 是唯一原始凭证认证层。
4. 下游服务只消费统一 Trusted Principal，不再二次认证原始凭证。

---

## 3. 最终结论

Datapillar 开放平台认证模型定版如下：

1. **平台只定义两种正式认证方式：`JWT`、`API_KEY`。**
2. **`JWT` 只用于 App API。**
3. **`API_KEY` 只用于 Open API。**
4. **App API 的入口域固定为 `/api/**`。**
5. **Open API 的入口域固定为 `/openapi/**`。**
6. **gateway 按路由域绑定认证方式，不做任何回退尝试或 credential 猜测。**
7. **`API_KEY` 的 HTTP 传输形式是 `Authorization: Bearer <api_key>`。**
8. **`API_KEY` 是平台机器主体，不是用户，不是隐藏用户。**
9. **gateway 对原始凭证完成认证后，统一向下游注入 Trusted Principal。**
10. **所有下游服务只做主体消费和授权判断，不再二次验证原始 JWT / API key。**
11. **租户管理员可以创建多个 API key。**
12. **API key DDL 直接归 `studio-service` 统一管理，写入现有 `V1__studio_schema.sql`。**

一句话总结：

**`JWT` 和 `API_KEY` 是两种明确的认证方式；`/api/**` 与 `/openapi/**` 是两类明确的入口域；gateway 之后只允许存在一套统一主体模型。**

---

## 4. 核心概念

## 4.1 认证方式

Datapillar 平台只支持两种认证方式：

### A. `JWT`

用于：

1. 前端用户登录态
2. 浏览器调用
3. 用户态 App API

### B. `API_KEY`

用于：

1. 开放平台程序化调用
2. SDK
3. Agent
4. 外部系统
5. OpenLineage 上报

注意：

**`API_KEY` 是认证方式，不是自定义 header 名。**

## 4.2 入口域

平台对外暴露两类入口域：

### A. App API

```text
/api/**
```

用途：

1. 用户前端
2. 人工操作
3. 用户态业务接口

认证方式：

```text
JWT
```

### B. Open API

```text
/openapi/**
```

用途：

1. 程序化调用
2. 机器接入
3. 平台集成
4. OpenLineage 接入

认证方式：

```text
API_KEY
```

这就是“显式指定认证类型”的核心：

**不是靠 token 猜，不是靠 fallback 试，而是通过入口域直接绑定认证方式。**

## 4.3 HTTP 传输形式

虽然 `JWT` 与 `API_KEY` 是两种认证方式，但 HTTP 头都统一使用：

```http
Authorization: Bearer <credential>
```

区别不在 header，而在：

1. 请求访问的是哪一个入口域
2. 该入口域绑定的是哪一种认证方式

因此：

- `/api/**` 上的 `Bearer` credential 被解释为 `JWT`
- `/openapi/**` 上的 `Bearer` credential 被解释为 `API_KEY`

这不是猜测，是协议。

---

## 5. 为什么不能再用 `X-Api-Key`

`X-Api-Key` 这种设计不符合当前目标。

原因：

1. 它把“认证方式”降级成了“header 技巧”。
2. 它和 OpenLineage 官方 `auth.type=api_key` 的使用方式不一致。
3. 它会让开放平台文档和 SDK 语义变得模糊。
4. 它会把本来应该显式的认证模型做成隐式约定。

正确模型是：

1. `API_KEY` 是正式认证方式
2. `Authorization: Bearer <api_key>` 是它的传输形式
3. `/openapi/**` 是它的入口域

---

## 6. 为什么不能做回退尝试

错误设计：

```text
拿到 Authorization
  -> 先试 JWT
  -> 失败再试 API_KEY
```

这个设计的问题：

1. 认证方式不明确
2. 路由契约不明确
3. 错误处理不明确
4. 文档和实际行为容易分裂
5. 安全边界会变脏

正确设计：

```text
/api/**      -> JWT authenticator
/openapi/**  -> API_KEY authenticator
```

即：

1. 路由先确定
2. 认证方式随路由确定
3. 再执行对应认证器

这才是开放平台该有的结构。

---

## 7. 平台路由模型

## 7.1 对外路由

建议平台对外公开如下入口域：

| 域 | 路径前缀 | 认证方式 | 面向对象 |
| --- | --- | --- | --- |
| App API | `/api/**` | `JWT` | 前端用户 |
| Open API | `/openapi/**` | `API_KEY` | 外部平台 / 机器 |

## 7.2 典型路径映射

| 对外路径 | 认证方式 | gateway 转发到 |
| --- | --- | --- |
| `/api/studio/**` | `JWT` | `/api/studio/**` |
| `/openapi/studio/**` | `API_KEY` | `/api/studio/**` |
| `/api/ai/**` | `JWT` | `/api/ai/**` |
| `/openapi/ai/**` | `API_KEY` | `/api/ai/**` |
| `/api/openlineage/**` | `JWT` | `/api/openlineage/**` |
| `/openapi/openlineage/**` | `API_KEY` | `/api/openlineage/**` |

也就是说：

**对外是两套路由域，对内仍然可以复用同一套后端业务路径。**

这不是两套 controller，也不是两套 service。

这是：

1. gateway 对外做两类入口
2. gateway 内部做 route rewrite
3. 后端继续只维护一套业务实现

## 7.3 明确不开放的域

以下路径不属于 Open API：

1. `/api/auth/**`
2. 用户登录、刷新、注销等会话接口

原因：

1. Open API 的核心是程序化访问业务能力
2. 登录态仍属于用户认证域

---

## 8. 主体模型

gateway 认证完成后，必须统一生成同一种内部主体：

```text
TrustedPrincipal
  - principalType
  - principalId
  - tenantId
  - tenantCode
  - displayName
  - roles
  - userId (nullable)
  - traceId
```

## 8.1 `JWT` 主体映射

```text
principalType = USER
principalId   = user:{userId}
tenantId      = current tenant id
tenantCode    = current tenant code
displayName   = username
roles         = current user roles
userId        = real user id
```

## 8.2 `API_KEY` 主体映射

```text
principalType = API_KEY
principalId   = api-key:{apiKeyId}
tenantId      = bound tenant id
tenantCode    = bound tenant code
displayName   = api key name
roles         = [ADMIN]
userId        = null
```

这里必须解释清楚：

`roles=[ADMIN]` 的含义是：

1. API key 不是用户
2. 但它在平台授权模型中承接租户级开放平台管理员权限
3. 这是机器主体的权限绑定，不是“伪装成用户”

这个映射是为了让现有大量基于 `ADMIN` 的授权入口继续可用，同时不污染主体语义。

---

## 9. 为什么 `API_KEY` 不是隐藏用户

“隐藏用户”方案必须明确否决。

错误方案：

```text
API_KEY
  -> hidden service account user
  -> fake userId
  -> downstream thinks it is a user
```

问题：

1. 主体语义错误
2. 机器调用伪装成人
3. 审计会混乱
4. 用户模型会被污染
5. 开放平台从一开始就立在错误抽象上

正确方案：

```text
API_KEY
  -> API_KEY principal
  -> gateway inject trusted principal
  -> downstream consumes principalType/principalId/tenant/roles
```

原则：

1. API key 有自己的 `principalType=API_KEY`
2. API key 有自己的 `principalId`
3. `userId` 对 API key 可为空
4. 主体识别通过 `principalType`，不是通过猜 `userId`

---

## 10. 用户如何知道“该用哪种认证方式”

这是平台契约必须显式暴露的内容，不能让用户猜。

平台必须在以下三个层面明确告知使用者：

## 10.1 控制台

在 API key 创建结果和详情页明确展示：

1. `Auth Type: API_KEY`
2. `API Domain: /openapi/**`
3. `Usage: Authorization: Bearer <api_key>`
4. `Example Curl`

创建 API key 的响应里也必须直接返回这些信息，而不是只给一串 key。

建议创建响应：

```json
{
  "id": 1001,
  "name": "default-open-platform-key",
  "authType": "API_KEY",
  "apiDomain": "/openapi/**",
  "headerName": "Authorization",
  "headerScheme": "Bearer",
  "plainApiKey": "dpk_xxx",
  "usageExample": "curl -H \"Authorization: Bearer dpk_xxx\" https://<gateway-host>/openapi/<service>/<path>"
}
```

## 10.2 API 文档

OpenAPI 文档必须显式定义两套 security scheme：

1. `jwtBearerAuth`
2. `apiKeyBearerAuth`

虽然两者都使用 Bearer 头，但它们是两种不同认证方式。

建议在 OpenAPI 中表达为：

```yaml
components:
  securitySchemes:
    jwtBearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
    apiKeyBearerAuth:
      type: http
      scheme: bearer
      bearerFormat: API_KEY
```

同时需要明确：

1. `jwtBearerAuth` 的描述中必须写明它只用于 `/api/**`
2. `apiKeyBearerAuth` 的描述中必须写明它只用于 `/openapi/**`
3. 运行时认证绑定仍然以 gateway 路由域为准

由于 gateway 会把 `/openapi/**` rewrite 到后端现有 `/api/**` 业务路径，下游服务生成的 OpenAPI 文档可以继续复用一套内部业务路径文档。

因此当前实现允许：

1. 受保护接口文档同时暴露 `jwtBearerAuth` 与 `apiKeyBearerAuth`
2. 在 scheme description / operation description 中明确 `/api/** -> JWT`、`/openapi/** -> API_KEY`

这样使用者看到接口文档就知道自己该填哪种认证方式。

## 10.3 SDK / 集成配置

集成方填写的应该是：

```yaml
auth:
  type: api_key
  credential: dpk_xxx
```

而不是让用户猜测“这个 token 到底应该塞到哪个 header”。

---

## 11. OpenLineage 集成如何使用

OpenLineage 集成在这套方案里只是 Open API 的一个使用场景。

它的目标入口应为：

```text
/openapi/openlineage/events
```

认证方式：

```text
API_KEY
```

HTTP 传输：

```http
Authorization: Bearer <api_key>
```

也就是说，OpenLineage 集成方感知到的是：

1. 认证类型：`api_key`
2. 目标地址：`/openapi/openlineage/events`
3. 不需要自定义 `X-Api-Key`

这和 OpenLineage 生态里“`auth.type=api_key`”的认知是一致的。

---

## 12. API_KEY 的产品语义

API key 的产品语义定义如下：

1. 它属于租户。
2. 它由租户管理员创建。
3. 它是该租户访问 Datapillar Open API 的凭证。
4. 它不是某个普通用户的个人 token。
5. 它不是 OpenLineage 专用 token。
6. 它不绑定 source。
7. 它不绑定 cluster。
8. 它不绑定隐藏用户。

所以：

1. 不需要 `sourceBindingId`
2. 不需要 `clusterBindingId`
3. 不需要额外“租户和 key 绑定表”

`tenant_api_keys` 自身就是绑定关系。

---

## 13. API_KEY 的权限语义

本方案下，`API_KEY` 是 Open API 的租户级管理员主体。

平台规则定义如下：

1. 每把 API key 默认承接该租户的开放平台管理员权限
2. gateway 将其统一映射到 `roles=[ADMIN]`
3. 因此 `/openapi/**` 能访问的平台能力范围与租户管理员对等

这意味着：

1. Open API 不是“低权限只读模式”
2. Open API 是租户管理员的程序化访问域
3. App API 与 Open API 的区别在认证方式和入口域，不在业务能力是否另起一套实现

这个设计是开放平台设计，不是 OpenLineage 特判设计。

---

## 14. DDL 与数据模型

## 14.1 DDL 归属

API key 的 DDL 统一归 `studio-service` 管理，并且：

**直接写入现有 `V1__studio_schema.sql`。**

不新建迁移文件。

## 14.2 表设计

表名：

`tenant_api_keys`

建议字段：

```sql
CREATE TABLE tenant_api_keys (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
  tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
  name VARCHAR(64) NOT NULL COMMENT 'API key name',
  description VARCHAR(255) NULL COMMENT 'API key description',
  key_hash CHAR(64) NOT NULL COMMENT 'SHA-256 hash of api key',
  last_four CHAR(4) NOT NULL COMMENT 'Last 4 chars for display',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 disabled',
  expires_at DATETIME NULL COMMENT 'Expiration time, NULL means never expires',
  last_used_at DATETIME NULL COMMENT 'Last used at',
  last_used_ip VARCHAR(64) NULL COMMENT 'Last used client IP',
  created_by BIGINT NOT NULL COMMENT 'Created by user ID',
  disabled_by BIGINT NULL COMMENT 'Disabled by user ID',
  disabled_at DATETIME NULL COMMENT 'Disabled at',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  UNIQUE KEY uq_tenant_api_key_hash (key_hash),
  UNIQUE KEY uq_tenant_api_key_name (tenant_id, name),
  KEY idx_tenant_api_key_status (tenant_id, status),
  KEY idx_tenant_api_key_last_used (tenant_id, last_used_at),
  CONSTRAINT fk_tenant_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_tenant_api_key_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_tenant_api_key_disabled_by FOREIGN KEY (disabled_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant API keys';
```

## 14.3 为什么数据库里只存 hash

正确做法：

1. 生成明文 key
2. 对明文做 `SHA-256`
3. 库里只保存 `key_hash`
4. 只展示 `last_four`
5. 请求进来时再次 hash 比对

API key 的用途是认证比对，不是后续拿出来回填第三方请求。

所以不需要可逆密文。

---

## 15. 管理规则

为了防止 API key 管理烂掉，平台必须有以下规则：

1. 一个租户可以创建多个 API key
2. 只有 `ADMIN` 可以创建、查看、禁用 API key
3. 同一租户活跃 key 数量有限制，例如默认最多 `10` 把
4. 同一租户内 `name` 必须唯一
5. 禁止硬删除，只允许禁用
6. 明文 key 只展示一次
7. 必须记录 `created_by`、`disabled_by`、`last_used_at`、`last_used_ip`
8. 允许设置 `expires_at`

---

## 16. 管理接口设计

这些接口属于 App API，由租户管理员通过 `JWT` 调用。

完整路径建议：

### 16.1 列表

```text
GET /api/studio/admin/tenant/current/api-keys
```

### 16.2 创建

```text
POST /api/studio/admin/tenant/current/api-keys
```

请求：

1. `name`
2. `description`
3. `expiresAt`

响应：

1. `id`
2. `name`
3. `authType`
4. `apiDomain`
5. `headerName`
6. `headerScheme`
7. `lastFour`
8. `expiresAt`
9. `createdAt`
10. `plainApiKey`
11. `usageExample`

### 16.3 禁用

```text
POST /api/studio/admin/tenant/current/api-keys/{id}/disable
```

---

## 17. gateway 认证设计

## 17.1 gateway 是唯一原始认证层

gateway 负责：

1. 接收外部请求
2. 根据路径识别入口域
3. 根据入口域绑定认证方式
4. 执行对应认证器
5. 生成统一 Trusted Principal
6. 注入可信身份头
7. 转发到下游服务

## 17.2 绑定规则

规则固定如下：

| 路径前缀 | 认证方式 |
| --- | --- |
| `/api/**` | `JWT` |
| `/openapi/**` | `API_KEY` |

注意：

1. `/api/**` 不接受 `API_KEY`
2. `/openapi/**` 不接受 `JWT`
3. 不是先试哪个再试哪个，而是路径决定认证方式

## 17.3 认证流程

### A. App API

```text
/api/**
  -> extract Authorization Bearer
  -> JWT authenticator
  -> USER principal
  -> inject trusted headers
  -> downstream
```

### B. Open API

```text
/openapi/**
  -> extract Authorization Bearer
  -> API_KEY authenticator
  -> API_KEY principal
  -> inject trusted headers
  -> downstream
```

## 17.4 gateway 注入的可信头

建议统一注入：

1. `X-Principal-Type`
2. `X-Principal-Id`
3. `X-Tenant-Id`
4. `X-Tenant-Code`
5. `X-Username`
6. `X-User-Roles`
7. `X-User-Id`
8. `X-Trace-Id`

规则：

1. `USER` 主体时 `X-User-Id` 必填
2. `API_KEY` 主体时 `X-User-Id` 可为空

---

## 18. 下游服务的职责

## 18.1 不再二次认证原始凭证

`studio-service`、`openlineage`、后续其他服务都不再：

1. 验证原始 JWT
2. 解析原始 API key
3. 调 auth / DB 再次校验原始凭证

也就是说：

**下游服务不再二次认证原始凭证。**

## 18.2 仍然要做的事情

下游服务仍然必须做：

1. 校验 trusted headers 是否存在且结构合法
2. 消费统一 Trusted Principal
3. 基于 `principalType / tenant / roles / userId` 做业务授权

所以要区分清楚：

1. **不再二次认证原始凭证**
2. **不是不做业务授权**

## 18.3 当前系统需要修正的地方

当前系统很多地方默认：

```text
userId != null 才是合法主体
```

这个规则必须改成：

```text
principalType = USER    -> userId required
principalType = API_KEY -> userId nullable
```

也就是说：

**主体合法性应由 `principalType` 决定，而不是由 `userId` 是否为空决定。**

---

## 19. `studio-service` 与 `auth` 的职责边界

这套方案里，`studio-service` 和 `auth` 的职责必须严格分开。

## 19.1 `studio-service` 的管理职责

1. 管理 `tenant_api_keys`
2. 提供 App API 管理接口
3. 只消费 gateway 注入的可信主体
4. 不解析原始 API key
5. 不承担 API key 原始认证

## 19.2 `auth` 的解析职责

`auth` 给 gateway 提供内部解析接口：

```text
POST /internal/security/api-keys/resolve
```

输入：

1. `apiKey`
2. `clientIp`
3. `traceId`

输出：

1. `principalType`
2. `principalId`
3. `userId`
4. `tenantId`
5. `tenantCode`
6. `tenantName`
7. `username`
8. `email`
9. `roles`
10. `impersonation`
11. `actorUserId`
12. `actorTenantId`
13. `sessionId`
14. `tokenId`

对 API key 固定返回：

```text
principalType = API_KEY
principalId   = api-key:{id}
tenantId      = bound tenant id
tenantCode    = bound tenant code
tenantName    = bound tenant name
username      = api key name
email         = null
roles         = [ADMIN]
userId        = null
impersonation = false
```

---

## 20. OpenLineage 在这套方案里的位置

OpenLineage 不再是例外系统。

它只是 Open API 的一个业务域。

因此：

1. App 用户侧使用：

```text
/api/openlineage/**
```

认证方式：

```text
JWT
```

2. 开放平台侧使用：

```text
/openapi/openlineage/**
```

认证方式：

```text
API_KEY
```

对于 OpenLineage 上报而言，推荐入口是：

```text
/openapi/openlineage/events
```

这样 OpenLineage 不需要单独发明一套认证体系。

---

## 21. 代码目录结构

本方案的代码落点应如下。

## 21.1 `datapillar-common`

```text
datapillar-common/
└── src/main/java/com/sunny/datapillar/common
    ├── constant
    │   └── HeaderConstants.java
    └── security
        ├── ApiKeyHashSupport.java
        ├── AuthType.java
        └── PrincipalType.java
```

职责：

1. 定义平台认证方式常量：`JWT`、`API_KEY`
2. 定义平台主体类型常量：`USER`、`API_KEY`
3. 定义统一可信头常量

## 21.2 `datapillar-studio-service`

```text
datapillar-studio-service/
├── src/main/resources
│   ├── db/migration
│   │   └── V1__studio_schema.sql
│   └── mapper/tenant
│       └── TenantApiKeyMapper.xml
├── src/main/java/com/sunny/datapillar/studio
│   ├── dto/tenant/request
│   │   └── TenantApiKeyCreateRequest.java
│   ├── dto/tenant/response
│   │   ├── TenantApiKeyItemResponse.java
│   │   ├── TenantApiKeyCreateResponse.java
│   │   └── ...
│   ├── module/tenant
│   │   ├── controller
│   │   │   └── TenantApiKeyAdminController.java
│   │   ├── entity
│   │   │   └── TenantApiKey.java
│   │   ├── mapper
│   │   │   └── TenantApiKeyMapper.java
│   │   ├── service
│   │   │   └── TenantApiKeyAdminService.java
│   │   └── service/impl
│   │       └── TenantApiKeyAdminServiceImpl.java
│   ├── filter
│   │   ├── IdentityStateValidationFilter.java
│   │   └── TrustedIdentityAuthenticationFilter.java
│   ├── config/openapi
│   │   └── OpenApiSecurityConfig.java
│   └── security/apikey
│       └── TenantApiKeyGenerator.java
└── src/test/java/com/sunny/datapillar/studio/module/tenant/service/impl
    └── TenantApiKeyAdminServiceImplTest.java
```

## 21.3 `datapillar-auth`

```text
datapillar-auth/
├── src/main/java/com/sunny/datapillar/auth
│   ├── api/internal
│   │   └── ApiKeyResolveController.java
│   ├── dto/auth/request
│   │   └── ApiKeyResolveRequest.java
│   ├── dto/auth/response
│   │   └── AuthenticationContextResponse.java
│   ├── entity
│   │   └── TenantApiKey.java
│   ├── mapper
│   │   └── TenantApiKeyMapper.java
│   └── service/impl
│       └── AuthServiceImpl.java
└── src/test/java/com/sunny/datapillar/auth/service/impl
    └── AuthServiceImplTest.java
```

职责：

1. 提供 `/internal/security/api-keys/resolve`
2. 读取 `tenant_api_keys` 并完成 hash 比对
3. 返回统一主体快照给 gateway
4. 更新 `last_used_at` 与 `last_used_ip`

## 21.4 `datapillar-api-gateway`

```text
datapillar-api-gateway/
└── src/main/java/com/sunny/datapillar/gateway
    ├── config
    │   └── AuthenticationProperties.java
    └── security
        ├── AccessTokenVerifier.java
        ├── ApiKeyAuthenticationContextClient.java
        ├── ApiKeyAuthenticationResolver.java
        ├── AuthAuthenticationContextClient.java
        ├── ClientIpResolver.java
        ├── AuthenticationFilter.java
        ├── IssuerJwksProvider.java
        ├── JwksAccessTokenVerifier.java
        ├── RouteAuthTypeResolver.java
        └── VerifiedAccessToken.java
```

职责：

1. 按路由域绑定认证方式
2. `JWT` 由 `AccessTokenVerifier` / `JwksAccessTokenVerifier` 验证
3. `API_KEY` 由 `ApiKeyAuthenticationResolver` 解析
4. 两类认证最终统一输出 `VerifiedAccessToken`

## 21.5 `datapillar-openlineage`

```text
datapillar-openlineage/
└── src/main/java/com/sunny/datapillar/openlineage/web
    ├── context
    │   └── TrustedIdentityContext.java
    ├── filter
    │   └── TrustedIdentityAuthenticationFilter.java
    └── service
        ├── EventService.java
        ├── EmbeddingService.java
        ├── QueryService.java
        └── RebuildService.java
```

职责：

1. 只消费 gateway 注入的统一主体
2. 不再解析原始 API key
3. 不再把 `userId` 当成唯一合法主体前提

---

## 22. 安全与审计

## 22.1 日志规则

日志中禁止打印完整明文 API key。

最多打印：

1. `apiKeyId`
2. `lastFour`
3. `tenantId`
4. `principalId`

## 22.2 最后使用记录

API key 解析成功后，应更新：

1. `last_used_at`
2. `last_used_ip`

## 22.3 轮转

平台采用双 key 轮转：

1. 创建新 key
2. 外部系统切换新 key
3. 验证调用正常
4. 禁用旧 key

## 22.4 高危认知

因为 `API_KEY` 承接的是租户级管理员权限，所以：

1. 它本质上是高权限开放平台凭证
2. 泄露后风险等同于租户管理员程序化访问能力泄露
3. 必须有审计、禁用、过期、轮转能力

---

## 23. 实施 TODO

以下 TODO 为当前设计文档对应的实现基线，开发阶段应按项打钩推进。

## 23.1 文档冻结与红线

- [x] 冻结当前文档为实现基线，不再改动认证模型核心结论
- [x] 明确红线：平台只定义 `JWT`、`API_KEY` 两种认证方式
- [x] 明确红线：`/api/**` 只走 `JWT`
- [x] 明确红线：`/openapi/**` 只走 `API_KEY`
- [x] 明确红线：`API_KEY` 只用 `Authorization: Bearer <api_key>`
- [x] 明确红线：禁止 `X-Api-Key`
- [x] 明确红线：禁止回退尝试、猜测式判型、fallback 认证
- [x] 明确红线：禁止隐藏用户、伪装 service account user

## 23.2 `datapillar-common`

- [x] 新增 `AuthType`
- [x] 新增 `PrincipalType`
- [x] 扩展 `HeaderConstants`
- [x] 统一可信头命名，避免各服务自行发明

## 23.3 `datapillar-studio-service` DDL

- [x] 在 `V1__studio_schema.sql` 中加入 `tenant_api_keys`
- [x] 校验唯一约束：`key_hash`
- [x] 校验唯一约束：`tenant_id + name`
- [x] 加入状态、过期、最后使用时间、最后使用 IP、创建人、禁用人字段
- [x] 校验外键均落在现有 `tenants/users`

## 23.4 `datapillar-studio-service` 管理能力

- [x] 新增 `TenantApiKey` entity
- [x] 新增 `TenantApiKeyMapper` 与对应 XML
- [x] 新增创建 API key 的 service
- [x] 新增列表 API key 的 service
- [x] 新增禁用 API key 的 service
- [x] 新增 API key 生成器
- [x] 复用 `datapillar-common` 中的 API key hash 支持
- [x] 创建接口只返回一次明文 key
- [x] 列表接口只返回 `lastFour` 和管理信息
- [x] 管理接口只允许 `ADMIN` 调用
- [x] 限制同租户活跃 key 数量
- [x] 记录 `last_used_at`、`last_used_ip`

## 23.5 `datapillar-auth` 内部解析接口

- [x] 新增 `/internal/security/api-keys/resolve`
- [x] 输入包含 `apiKey`、`clientIp`、`traceId`
- [x] 输出统一主体快照，而不是原始数据库记录
- [x] 固定返回 `principalType=API_KEY`
- [x] 固定返回 `principalId=api-key:{id}`
- [x] 固定返回 `roles=[ADMIN]`
- [x] 对 `API_KEY` 明确允许 `userId=null`
- [x] 处理禁用、过期、租户禁用等拒绝场景

## 23.6 `datapillar-api-gateway`

- [x] 新增 Open API 路由前缀 `/openapi/**`
- [x] 保留 App API 路由前缀 `/api/**`
- [x] 增加路由域到认证方式的硬绑定
- [x] `/api/**` 只允许 `JWT`
- [x] `/openapi/**` 只允许 `API_KEY`
- [x] `AuthenticationFilter` 去掉任何 fallback / 猜测逻辑
- [x] 新增 `ApiKeyAuthenticationResolver`
- [x] 使用 `AccessTokenVerifier` / `JwksAccessTokenVerifier` 验证 `JWT`
- [x] 两类认证最终统一输出 `VerifiedAccessToken`
- [x] gateway 注入统一可信主体头
- [x] 拒绝客户端伪造可信主体头
- [x] Open API 路由 rewrite 到后端现有 `/api/**` 业务路径

## 23.7 `datapillar-openlineage`

- [x] 支持 `/openapi/openlineage/**` 通过 gateway 转发到现有 `/api/openlineage/**`
- [x] `TrustedIdentityAuthenticationFilter` 改为基于 `principalType` 校验主体合法性
- [x] 去掉“`userId != null` 才是合法主体”的硬编码
- [x] `API_KEY` 主体下允许 `userId=null`
- [x] `EventService` 等服务改为消费统一主体
- [x] 不再解析原始 API key
- [x] 不再二次认证原始凭证

## 23.8 `datapillar-studio-service` 其他用户态服务

- [x] `TrustedIdentityAuthenticationFilter` 同样改为基于 `principalType`
- [ ] 当前依赖 `userId != null` 的入口逐个梳理
- [ ] 明确哪些接口要求 `USER` 主体，哪些接口接受 `API_KEY`
- [ ] 不再把 `userId` 当唯一主体判断依据

## 23.9 OpenAPI 与产品暴露

- [x] 在 OpenAPI 中定义 `jwtBearerAuth`
- [x] 在 OpenAPI 中定义 `apiKeyBearerAuth`
- [x] 在 security scheme description 中明确 `/api/** -> JWT`
- [x] 在 security scheme description 中明确 `/openapi/** -> API_KEY`
- [x] 受保护接口文档补充 gateway context 说明
- [x] 创建 API key 响应中返回 `authType`
- [x] 创建 API key 响应中返回 `apiDomain`
- [x] 创建 API key 响应中返回 `usageExample`
- [ ] 控制台 API key 详情页展示认证方式、入口域、Bearer 用法

## 23.10 测试

- [x] `studio-service` API key 管理 service 单测
- [x] `auth` API key resolve 单测
- [x] gateway `/api/** + JWT` 成功路径测试
- [x] gateway `/api/** + API_KEY` 拒绝测试
- [x] gateway `/openapi/** + API_KEY` 成功路径测试
- [x] gateway `/openapi/** + JWT` 拒绝测试
- [x] gateway 伪造 trusted headers 拒绝测试
- [x] openlineage `API_KEY` 主体通过测试
- [x] openlineage `USER` 主体兼容测试
- [x] `userId=null + principalType=API_KEY` 合法性测试

## 23.11 收尾

- [ ] 补平台使用文档示例
- [ ] 补 OpenLineage 接入示例
- [ ] 自测 `/api/**` 与 `/openapi/**` 路由隔离
- [ ] 自测旧 JWT 链路无回归

---

## 24. Coding 准入与跑偏风险

## 24.1 是否可以开始 coding

可以。

当前文档已经足够作为实现基线，不需要再补架构设计。

真正风险不在“设计还没想明白”，而在“实现时为了省事把设计做歪”。

## 24.2 实现中最容易跑偏的地方

以下行为一旦出现，就说明实现已经开始偏离当前方案：

- [ ] 把 `/openapi/**` 也做成支持 `JWT`
- [ ] 把 `/api/**` 也顺手支持 `API_KEY`
- [ ] 恢复 `X-Api-Key`
- [ ] 在 gateway 里写“先试 JWT，再试 API_KEY”
- [ ] 用隐藏用户 / fake `userId` 兜底
- [ ] 下游服务继续把 `userId != null` 当唯一合法主体
- [ ] Open API 不做 route rewrite，反而复制一套业务 controller
- [ ] 把 `API_KEY` 只做成 OpenLineage 特判，不做平台认证方式

## 24.3 判断 coding 是否仍然对齐设计的标准

只要以下判断全部成立，实现就没有跑偏：

- [ ] 外部认证方式仍然只有 `JWT` / `API_KEY`
- [ ] 路由域仍然只有 `/api/**` / `/openapi/**`
- [ ] gateway 仍然是唯一原始凭证认证层
- [ ] 下游服务仍然只消费统一 Trusted Principal
- [ ] `API_KEY` 仍然不是隐藏用户
- [ ] OpenLineage 仍然只是 Open API 的一个业务域，不是例外系统

---

## 25. 最终定版

Datapillar 开放平台 API_KEY 认证方案最终定版如下：

1. **平台只定义两种认证方式：`JWT`、`API_KEY`。**
2. **`/api/**` 明确绑定 `JWT`。**
3. **`/openapi/**` 明确绑定 `API_KEY`。**
4. **gateway 按路由域执行认证器，不做任何回退尝试或猜测。**
5. **`API_KEY` 使用 `Authorization: Bearer <api_key>` 传输。**
6. **OpenLineage 等外部集成统一走 `/openapi/**`。**
7. **gateway 是唯一原始凭证认证层。**
8. **下游服务不再二次认证原始凭证，只消费统一 Trusted Principal。**
9. **`API_KEY` 是平台机器主体，不是隐藏用户。**
10. **租户管理员可以创建多个 API key。**
11. **API key 数据库存 hash，不存明文。**
12. **DDL 直接添加到 `studio-service` 的 `V1__studio_schema.sql`。**

真正关键的不是“再加一张表”。

真正关键的是：

**Datapillar 必须先把 `API_KEY` 明确定义成开放平台认证方式，再把 gateway 之后的所有服务统一收敛到一套主体模型。**
