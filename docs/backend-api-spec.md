# Datapillar 后端 API 统一规范与接口清单

适用范围：`datapillar-auth`、`datapillar-workbench-service`

目标：
- 所有接口统一响应结构、统一状态码语义
- 所有错误由统一入口处理（不允许 controller/service 各写各的）
- 前端只需在一个地方判断状态与错误

---

## 1. 统一响应结构（强制）

统一返回结构（所有接口必须一致）：

```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/login",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

字段说明：
- `status`：HTTP 状态码，必须与响应码一致
- `code`：业务码，保留用于精确定位问题
- `message`：面向用户/前端的错误说明
- `data`：成功时按接口语义返回（列表=数组，非列表=对象/字符串/空），失败固定为 `null`
- `limit`：分页大小（仅分页接口返回）
- `offset`：偏移量（仅分页接口返回，首条为 0）
- `total`：总记录数（仅分页接口返回）
- `timestamp`：ISO 时间
- `path`：请求路径
- `traceId`：链路 ID（从 MDC 读取，可为空）

数据与分页规则（强制）：
- 列表接口：`data` 必须是数组，禁止 `records` 包装
- 非列表接口：`data` 按业务语义返回，不强制数组
- 分页字段放在顶层：`limit`、`offset`、`total`（仅分页接口返回）
- 失败响应：`data` 固定为 `null`

### 分页响应示例（列表接口）

```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    { "id": 1, "name": "示例" }
  ],
  "limit": 10,
  "offset": 0,
  "total": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 失败示例

```json
{
  "status": 401,
  "code": "AUTH_REFRESH_TOKEN_EXPIRED",
  "message": "refresh token 已过期",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/refresh",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

---

## 2. 统一处理入口（禁止“东一块西一块”）

强制规则：
- Controller 只能 `return ApiResponse.ok(data)` 或 `throw new BusinessException(ErrorCode.XXX)`
- 不允许 Controller 手写 try/catch 拼接错误响应
- 所有异常统一由 `@RestControllerAdvice` 处理
- 安全层 401/403 必须走统一 JSON 输出

统一入口（每个模块都必须有）：
- `ApiResponse`：统一响应结构 + 工厂方法（归属 web 层）
- `ErrorCode`：业务码 + HTTP 状态码
- `BusinessException`：业务异常，携带 ErrorCode
- `SystemException`：系统异常（统一映射为 `COMMON_INTERNAL_ERROR`）
- `BaseGlobalExceptionHandler`：集中处理所有异常
- `SecurityEntryPoint/AccessDeniedHandler`：统一输出 401/403 JSON

> 这不是建议，是约束。否则前端判断永远会碎片化。

---

### 2.1 异常规范（全项目）

适用范围：Web 接口、任务/调度、批处理、内部服务调用。

规则：
- 业务异常统一用 `BusinessException`（携带 ErrorCode）
- 系统异常统一用 `SystemException`（对外固定 `COMMON_INTERNAL_ERROR`，细节只进日志）
- 非 HTTP 场景不直接拼 JSON，由上层统一处理或包装为错误对象

## 3. 错误码与 HTTP 状态码映射

业务码规范（强制）：
- 成功码固定 `OK`
- 错误码必须加模块前缀：`AUTH_*` / `ADMIN_*` / `COMMON_*`

### 3.1 Auth（datapillar-auth）

| 业务码 | HTTP 状态码 | 说明 |
|---|---|---|
| OK | 200 | 成功 |
| AUTH_VALIDATION_ERROR / AUTH_INVALID_ARGUMENT | 400 | 参数错误 |
| AUTH_UNAUTHORIZED / AUTH_TOKEN_* / AUTH_REFRESH_TOKEN_EXPIRED / AUTH_INVALID_CREDENTIALS / AUTH_USER_NOT_FOUND | 401 | 未授权或 token 异常 |
| AUTH_FORBIDDEN / AUTH_USER_DISABLED | 403 | 无权限或禁用 |
| AUTH_DUPLICATE_KEY | 409 | 资源冲突 |
| AUTH_INTERNAL_ERROR | 500 | 服务异常 |

### 3.2 Workbench Service（datapillar-workbench-service）

| 业务码 | HTTP 状态码 | 说明 |
|---|---|---|
| OK | 200 | 成功 |
| ADMIN_VALIDATION_ERROR / ADMIN_INVALID_ARGUMENT | 400 | 参数错误 |
| ADMIN_UNAUTHORIZED / ADMIN_USER_NOT_LOGGED_IN | 401 | 未授权 |
| ADMIN_FORBIDDEN / ADMIN_PROJECT_ACCESS_DENIED | 403 | 无权限 |
| ADMIN_*_NOT_FOUND / ADMIN_RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| ADMIN_DUPLICATE_RESOURCE / ADMIN_DUPLICATE_KEY | 409 | 资源冲突 |
| ADMIN_INTERNAL_ERROR | 500 | 服务异常 |

---

## 4. datapillar-auth 接口清单与案例

Base Path: `/auth`

### 4.1 POST /auth/login
说明：登录（支持两阶段）

请求示例：
```json
{
  "tenantCode": "demo",
  "username": "sunny",
  "password": "123456asd",
  "rememberMe": true,
  "inviteCode": "X9P7-ABCD",
  "email": "sunny@datapillar.com",
  "phone": "13800000000"
}
```

说明：
- `tenantCode` 可选：为空时返回租户列表，由前端选择租户。
- `inviteCode` 仅在首次入库时必填；已存在 `tenant_users` 的用户可不传。
- 未携带有效 `inviteCode` 的首次登录请求直接拒绝。
- 若邀请填写了邮箱/手机号，登录请求中的邮箱/手机号必须匹配邀请。

响应示例（单租户直接登录）：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "loginStage": "SUCCESS",
    "userId": 1,
    "tenantId": 10,
    "username": "sunny",
    "email": "sunny@datapillar.com",
    "roles": [
      { "id": 1, "name": "超级管理员", "type": "ADMIN" }
    ],
    "menus": [
      {
        "id": 1,
        "name": "项目",
        "path": "/projects",
        "permissionCode": "READ",
        "location": "TOP",
        "categoryId": 1,
        "categoryName": "项目管理",
        "children": []
      }
    ]
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/login",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

响应示例（多租户需选择）：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "loginStage": "TENANT_SELECT",
    "loginToken": "<short-lived>",
    "tenants": [
      { "tenantId": 1, "tenantCode": "demo", "tenantName": "Demo", "status": 1, "isDefault": 1 },
      { "tenantId": 2, "tenantCode": "prod", "tenantName": "Prod", "status": 1, "isDefault": 0 }
    ]
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/login",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.2 POST /auth/login/tenant
说明：选择租户后完成登录

请求示例：
```json
{
  "loginToken": "<short-lived>",
  "tenantId": 10
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "loginStage": "SUCCESS",
    "userId": 1,
    "tenantId": 10,
    "username": "sunny",
    "email": "sunny@datapillar.com",
    "roles": [
      { "id": 1, "name": "超级管理员", "type": "ADMIN" }
    ],
    "menus": []
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/login/tenant",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.3 POST /auth/refresh
说明：刷新 Token（使用 Cookie 中的 refresh-token）

默认过期策略：
- Access Token：60 分钟（`JWT_ACCESS_EXPIRATION`）
- Refresh Token：未勾选记住我 7 天（`JWT_REFRESH_EXPIRATION`）
- Refresh Token：勾选记住我 30 天（`JWT_REFRESH_REMEMBER_EXPIRATION`）

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "userId": 1,
    "tenantId": 10,
    "username": "sunny",
    "email": "sunny@datapillar.com",
    "roles": [],
    "permissions": [],
    "menus": []
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/refresh",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

失败示例（refresh 过期）：
```json
{
  "status": 401,
  "code": "AUTH_REFRESH_TOKEN_EXPIRED",
  "message": "refresh token 已过期",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/refresh",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.4 POST /auth/validate
说明：验证 Token

请求示例：
```json
{
  "token": "<access-token>",
  "refreshToken": "<refresh-token>"
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "valid": true,
    "userId": 1,
    "tenantId": 10,
    "username": "sunny",
    "email": "sunny@datapillar.com",
    "errorMessage": null
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/validate",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.5 GET /auth/sso/qr
说明：获取 SSO 扫码配置

请求示例：
```
GET /auth/sso/qr?tenantCode=demo&provider=dingtalk
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "type": "SDK",
    "state": "b1c2d3e4f5a6",
    "payload": {
      "clientId": "dingxxx",
      "redirectUri": "https://demo.example.com/auth/callback",
      "scope": "openid corpid",
      "responseType": "code",
      "prompt": "consent",
      "corpId": "dingcorp"
    }
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/sso/qr",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

说明：
- `state` 为一次性校验码，必须在 `/auth/sso/login` 中原样回传。
- `payload` 用于前端 SDK/跳转授权；字段由不同 provider 决定。

### 4.6 POST /auth/sso/login
说明：SSO 登录（授权码模式）

请求示例：
```json
{
  "tenantCode": "demo",
  "provider": "dingtalk",
  "authCode": "<auth-code>",
  "state": "<state>",
  "inviteCode": "X9P7-ABCD"
}
```

说明：
- `inviteCode` 仅在首次入库时必填；已存在 `tenant_users` 的用户可不传。
- 若邀请填写了邮箱/手机号，SSO 回调的已验证邮箱/手机号必须匹配邀请，否则拒绝。
- `provider` 必填，用于区分不同 OA/IdP。

响应示例（单租户直接登录）：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "loginStage": "SUCCESS",
    "userId": 1,
    "tenantId": 10,
    "username": "sunny",
    "email": "sunny@datapillar.com",
    "roles": [
      { "id": 1, "name": "超级管理员", "type": "ADMIN" }
    ],
    "menus": []
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/sso/login",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

说明：
- 若用户存在多个可用租户，将返回 `loginStage=TENANT_SELECT` + `loginToken` + `tenants`，与 `/auth/login` 一致。

### 4.7 POST /auth/logout
说明：登出

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": "登出成功",
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/logout",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.8 GET /auth/token-info
说明：获取 Token 信息（状态查询接口）

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "valid": true,
    "remainingSeconds": 1200,
    "expirationTime": 1735689600000,
    "issuedAt": 1735686000000,
    "userId": 1,
    "tenantId": 10,
    "username": "sunny"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/token-info",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 4.9 GET /auth/health
说明：健康检查

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": "OK",
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/auth/health",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

---

## 5. datapillar-workbench-service 接口清单与案例

### 5.1 健康检查

GET /health

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": "OK",
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/health",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.2 SQL

POST /sql/execute

请求示例：
```json
{
  "sql": "select * from users limit 10",
  "catalog": "prod",
  "database": "datapillar",
  "maxRows": 100
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "success": true,
    "error": null,
    "columns": [
      { "name": "id", "type": "BIGINT", "nullable": false },
      { "name": "name", "type": "VARCHAR", "nullable": true }
    ],
    "rows": [
      [1, "alice"],
      [2, "bob"]
    ],
    "rowCount": 2,
    "hasMore": false,
    "executionTime": 120,
    "message": "OK"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/sql/execute",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.3 项目管理

GET /users/{userId}/projects

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "数据治理",
      "description": "治理项目",
      "ownerId": 1,
      "ownerName": "sunny",
      "status": "active",
      "tags": ["core"],
      "isFavorite": true,
      "isVisible": true,
      "memberCount": 3,
      "lastAccessedAt": "2025-01-01T10:00:00",
      "createdAt": "2025-01-01T09:00:00",
      "updatedAt": "2025-01-01T11:00:00"
    }
  ],
  "limit": 10,
  "offset": 0,
  "total": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{userId}/projects/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "数据治理",
    "description": "治理项目",
    "ownerId": 1,
    "ownerName": "sunny",
    "status": "active",
    "tags": ["core"],
    "isFavorite": true,
    "isVisible": true,
    "memberCount": 3,
    "lastAccessedAt": "2025-01-01T10:00:00",
    "createdAt": "2025-01-01T09:00:00",
    "updatedAt": "2025-01-01T11:00:00"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users/{userId}/projects

请求示例：
```json
{
  "name": "数据治理",
  "description": "治理项目",
  "tags": ["core"],
  "isVisible": true
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{userId}/projects/{id}

请求示例：
```json
{
  "name": "数据治理-更新",
  "description": "治理项目",
  "status": "active",
  "tags": ["core"],
  "isFavorite": true,
  "isVisible": true
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /users/{userId}/projects/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.4 用户管理

GET /users

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "username": "sunny",
      "nickname": "Sunny",
      "email": "sunny@datapillar.com",
      "phone": "13800000000",
      "status": 1,
      "createdAt": "2025-01-01T09:00:00",
      "updatedAt": "2025-01-01T11:00:00",
      "roles": [],
      "permissions": []
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "sunny",
    "nickname": "Sunny",
    "email": "sunny@datapillar.com",
    "phone": "13800000000",
    "status": 1,
    "createdAt": "2025-01-01T09:00:00",
    "updatedAt": "2025-01-01T11:00:00",
    "roles": [],
    "permissions": []
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users

请求示例：
```json
{
  "username": "sunny",
  "password": "123456asd",
  "nickname": "Sunny",
  "email": "sunny@datapillar.com",
  "phone": "13800000000",
  "status": 1,
  "roleIds": [1]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{id}

请求示例：
```json
{
  "nickname": "Sunny",
  "email": "sunny@datapillar.com",
  "phone": "13800000000",
  "status": 1,
  "roleIds": [1]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /users/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{id}/roles

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "type": "ADMIN",
      "name": "管理员",
      "description": "系统管理员",
      "status": 1,
      "sort": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/roles",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{id}/roles

请求示例：
```json
[1, 2]
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/roles",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{id}/menus

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "parentId": null,
      "name": "项目",
      "path": "/projects",
      "component": "ProjectPage",
      "icon": "Folder",
      "permissionCode": "PROJECT_READ",
      "visible": 1,
      "sort": 1,
      "createdAt": "2025-01-01T09:00:00",
      "updatedAt": "2025-01-01T11:00:00",
      "children": []
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/menus",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.4.1 邀请管理

POST /users/{userId}/invitations

请求示例：
```json
{
  "inviteeEmail": "alice@company.com",
  "inviteeMobile": null,
  "orgIds": [10, 12],
  "roleIds": [3, 4],
  "expiresAt": "2025-02-10T12:00:00"
}
```

说明：
- `inviteeEmail` 与 `inviteeMobile` 至少一个必填。
- 同一租户同一邀请对象同一时间只允许一条待接受邀请。

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1001,
    "inviteCode": "X9P7-ABCD",
    "inviteUrl": "https://<domain>/invite?code=X9P7-ABCD",
    "expiresAt": "2025-02-10T12:00:00"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/invitations",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{userId}/invitations?status=0&keyword=alice&offset=0&limit=20

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1001,
      "inviteeEmail": "alice@company.com",
      "inviteeMobile": null,
      "status": 0,
      "expiresAt": "2025-02-10T12:00:00",
      "orgIds": [10, 12],
      "roleIds": [3, 4],
      "createdAt": "2025-02-01T12:00:00",
      "updatedAt": "2025-02-01T12:00:00"
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/invitations",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{userId}/invitations/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1001,
    "inviteeEmail": "alice@company.com",
    "inviteeMobile": null,
    "status": 0,
    "expiresAt": "2025-02-10T12:00:00",
    "orgIds": [10, 12],
    "roleIds": [3, 4],
    "createdAt": "2025-02-01T12:00:00",
    "updatedAt": "2025-02-01T12:00:00"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/invitations/1001",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PATCH /users/{userId}/invitations/{id}

请求示例：
```json
{
  "status": 3
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/invitations/1001",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users/{userId}/invitations/{id}/resend

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": "OK",
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/invitations/1001/resend",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.5 角色管理

GET /roles

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "type": "ADMIN",
      "name": "管理员",
      "description": "系统管理员",
      "status": 1,
      "sort": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /roles/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "type": "ADMIN",
    "name": "管理员",
    "description": "系统管理员",
    "status": 1,
    "sort": 1
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /roles

请求示例：
```json
{
  "type": "ADMIN",
  "name": "管理员",
  "description": "系统管理员",
  "permissions": [
    { "objectId": 1, "permissionCode": "READ" },
    { "objectId": 2, "permissionCode": "WRITE" }
  ]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /roles/{id}

请求示例：
```json
{
  "name": "管理员",
  "description": "系统管理员",
  "permissions": [
    { "objectId": 1, "permissionCode": "READ" },
    { "objectId": 2, "permissionCode": "WRITE" }
  ]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /roles/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /roles/{id}/permissions

请求示例：
```json
{
  "permissions": [
    { "objectId": 1, "permissionCode": "READ" },
    { "objectId": 2, "permissionCode": "WRITE" }
  ]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/roles/1/permissions",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.6 功能授权（租户功能权限）

说明：
- `permissionCode` 仅允许系统静态权限（`permissions.tenant_id=0`），如 `READ/WRITE/ADMIN`。
- `status`：1 启用，0 禁用。

GET /features

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "objectId": 1,
      "objectName": "项目",
      "objectPath": "/projects",
      "objectType": "MENU",
      "location": "SIDEBAR",
      "categoryId": 10,
      "categoryName": "项目管理",
      "sort": 10,
      "objectStatus": 1,
      "entitlementStatus": 1,
      "permissionCode": "READ",
      "permissionLevel": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/features",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /features/{featureId}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "objectId": 1,
    "objectName": "项目",
    "objectPath": "/projects",
    "objectType": "MENU",
    "location": "SIDEBAR",
    "categoryId": 10,
    "categoryName": "项目管理",
    "sort": 10,
    "objectStatus": 1,
    "entitlementStatus": 1,
    "permissionCode": "READ",
    "permissionLevel": 1
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/features/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /features/{featureId}/entitlement

请求示例：
```json
{
  "permissionCode": "READ",
  "status": 1
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/features/1/entitlement",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /features/entitlements

请求示例：
```json
{
  "items": [
    { "objectId": 1, "permissionCode": "READ", "status": 1 },
    { "objectId": 2, "permissionCode": "WRITE", "status": 1 }
  ]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/features/entitlements",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.7 工作流管理（业务接口）

GET /users/{userId}/projects/{projectId}/workflows

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "projectId": 1,
      "projectName": "数据治理",
      "workflowName": "每日同步",
      "triggerType": 1,
      "status": 1,
      "description": "每天跑一次",
      "jobCount": 3,
      "createdAt": "2025-01-01T09:00:00",
      "updatedAt": "2025-01-01T11:00:00"
    }
  ],
  "limit": 10,
  "offset": 0,
  "total": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{userId}/projects/{projectId}/workflows/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "projectId": 1,
    "projectName": "数据治理",
    "workflowName": "每日同步",
    "triggerType": 1,
    "triggerValue": "0 0 * * *",
    "timeoutSeconds": 3600,
    "maxRetryTimes": 3,
    "priority": 5,
    "status": 1,
    "description": "每天跑一次",
    "createdAt": "2025-01-01T09:00:00",
    "updatedAt": "2025-01-01T11:00:00",
    "jobs": [],
    "dependencies": []
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users/{userId}/projects/{projectId}/workflows

请求示例：
```json
{
  "workflowName": "每日同步",
  "triggerType": 1,
  "triggerValue": "0 0 * * *",
  "timeoutSeconds": 3600,
  "maxRetryTimes": 3,
  "priority": 5,
  "description": "每天跑一次"
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{userId}/projects/{projectId}/workflows/{id}

请求示例：
```json
{
  "workflowName": "每日同步",
  "triggerType": 1,
  "triggerValue": "0 0 * * *",
  "timeoutSeconds": 3600,
  "maxRetryTimes": 3,
  "priority": 5,
  "description": "每天跑一次"
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /users/{userId}/projects/{projectId}/workflows/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.8 工作流管理（Airflow 透传接口）

以下接口返回 `data` 为 Airflow 原始响应，本服务不做二次映射，字段以 Airflow API 为准。

- POST /users/{userId}/projects/{projectId}/workflows/{id}/publish
- POST /users/{userId}/projects/{projectId}/workflows/{id}/pause
- POST /users/{userId}/projects/{projectId}/workflows/{id}/resume
- GET /users/{userId}/projects/{projectId}/workflows/{id}/dag
- GET /users/{userId}/projects/{projectId}/workflows/{id}/dag/versions
- GET /users/{userId}/projects/{projectId}/workflows/{id}/dag/versions/{versionNumber}
- POST /users/{userId}/projects/{projectId}/workflows/{id}/runs
- GET /users/{userId}/projects/{projectId}/workflows/{id}/runs
- GET /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}
- GET /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/jobs
- GET /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/jobs/{jobId}
- GET /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/jobs/{jobId}/logs
- POST /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/jobs/{jobId}/rerun
- PATCH /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/jobs/{jobId}/state
- POST /users/{userId}/projects/{projectId}/workflows/{id}/runs/{runId}/clear

统一响应示例（以 dag 详情为例）：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/dag",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.9 任务管理（工作流内）

GET /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "workflowId": 1,
      "jobName": "抽取",
      "jobType": 1,
      "jobTypeCode": "SQL",
      "jobTypeName": "SQL",
      "jobParams": {"sql": "select 1"},
      "timeoutSeconds": 0,
      "maxRetryTimes": 0,
      "retryInterval": 0,
      "priority": 0,
      "positionX": 100,
      "positionY": 200,
      "description": "",
      "createdAt": "2025-01-01T09:00:00",
      "updatedAt": "2025-01-01T11:00:00"
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "workflowId": 1,
    "jobName": "抽取",
    "jobType": 1,
    "jobTypeCode": "SQL",
    "jobTypeName": "SQL",
    "jobParams": {"sql": "select 1"},
    "timeoutSeconds": 0,
    "maxRetryTimes": 0,
    "retryInterval": 0,
    "priority": 0,
    "positionX": 100,
    "positionY": 200,
    "description": "",
    "createdAt": "2025-01-01T09:00:00",
    "updatedAt": "2025-01-01T11:00:00"
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs

请求示例：
```json
{
  "jobName": "抽取",
  "jobType": 1,
  "jobParams": {"sql": "select 1"},
  "timeoutSeconds": 0,
  "maxRetryTimes": 0,
  "retryInterval": 0,
  "priority": 0,
  "positionX": 100,
  "positionY": 200,
  "description": ""
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs/{id}

请求示例：
```json
{
  "jobName": "抽取",
  "jobParams": {"sql": "select 1"},
  "priority": 1
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs/{id}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

PUT /users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs/layout

请求示例：
```json
{
  "positions": [
    { "jobId": 1, "positionX": 120, "positionY": 260 }
  ]
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/jobs/layout",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.10 组件管理

GET /components

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "componentCode": "SQL",
      "componentName": "SQL",
      "componentType": "SQL",
      "jobParams": {"sql": ""},
      "description": "SQL 任务",
      "icon": "Database",
      "color": "#3b82f6",
      "sortOrder": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/components",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /components/code/{code}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": 1,
    "componentCode": "SQL",
    "componentName": "SQL",
    "componentType": "SQL",
    "jobParams": {"sql": ""},
    "description": "SQL 任务",
    "icon": "Database",
    "color": "#3b82f6",
    "sortOrder": 1
  },
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/components/code/SQL",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

GET /components/type/{type}

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "componentCode": "SQL",
      "componentName": "SQL",
      "componentType": "SQL",
      "jobParams": {"sql": ""},
      "description": "SQL 任务",
      "icon": "Database",
      "color": "#3b82f6",
      "sortOrder": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/components/type/SQL",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 5.11 依赖管理

GET /users/{userId}/projects/{projectId}/workflows/{workflowId}/dependencies

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "workflowId": 1,
      "jobId": 2,
      "parentJobId": 1
    }
  ],
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/dependencies",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

POST /users/{userId}/projects/{projectId}/workflows/{workflowId}/dependencies

请求示例：
```json
{
  "jobId": 2,
  "parentJobId": 1
}
```

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": 1,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/dependencies",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

DELETE /users/{userId}/projects/{projectId}/workflows/{workflowId}/dependencies?jobId=2&parentJobId=1

响应示例：
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "path": "/users/1/projects/1/workflows/1/dependencies",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

---

## 6. 统一实现落点（代码层约束）

### 6.1 公共模块（强制）

统一错误码与异常必须下沉到公共模块，Web 响应与安全输出下沉到各业务模块，避免职责混乱。

模块与分层（单模块内分包）：
- 模块名：`datapillar-common`
- 目录分层：
  - `com.sunny.datapillar.common.error`：错误码定义（统一 `ErrorCode`）
  - `com.sunny.datapillar.common.exception`：异常体系（`BusinessException`/`SystemException`）

错误码规则（强制）：
- 不再按模块拆分多个枚举文件
- 所有错误码统一维护在 `ErrorCode` 中，按前缀分区（`AUTH_`/`ADMIN_`/`COMMON_`）
- 新业务仅新增常量，不新增 `XXXErrorCode` 类

Maven 约束：
- 根 `pom.xml` 增加模块：`datapillar-common`
- `datapillar-auth` 与 `datapillar-workbench-service` 必须依赖该模块

依赖示例（模块 `pom.xml`）：
```xml
<dependency>
  <groupId>com.sunny</groupId>
  <artifactId>datapillar-common</artifactId>
  <version>${project.version}</version>
</dependency>
```

### 6.2 各模块落点（auth / workbench-service）

每个模块必须保证以下落点，且所有接口共用：

- `web/response`：`ApiResponse`（模块内）
- `web/handler`：`BaseGlobalExceptionHandler`（模块内）
- `web/security`：统一 401/403 输出（模块内）

- `error/*`：不允许自建错误码枚举（统一引用 `common.error.ErrorCode`）
- `config/GlobalExceptionHandler`：仅做模块特例处理，其他委托模块内 `BaseGlobalExceptionHandler`
- `config/SecurityExceptionHandler`：统一 401/403 JSON（调用模块内 `SecurityErrorWriter`）

Controller 必须只有两种写法：
- `return ApiResponse.ok(data)`
- `throw new BusinessException(ErrorCode.XXX)`

不允许：在 controller/service 拼 `code/message/data`，不允许 try/catch 打补丁。
