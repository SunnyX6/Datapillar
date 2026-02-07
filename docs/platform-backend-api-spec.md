# Datapillar Studio 后端接口规范（MVP）

> 注意：平台服务已移除，本规范保留历史参考，细节以当前 `datapillar-studio-service` 实现为准。

适用范围：`datapillar-studio-service`

目标：
- Studio 控制面统一对外 API（租户/IAM/授权/邀请/SSO）
- 响应结构与错误规范沿用 `docs/backend-api-spec.md`

---

## 1. 基础约束

**Base Path**：`/api/studio`

**统一响应**：使用 `ApiResponse<T>`（字段与分页规则见 `docs/backend-api-spec.md`）

**核心规则（强制）**
- 功能资源、权限字典为系统预置，不提供创建/修改接口
- 租户功能上限 ≠ 用户权限：
  - `tenant_feature_permissions` 为租户上限
  - `role_permissions / user_permission_overrides` 为用户实际权限

---

## 2. 通用响应示例

### 2.1 列表响应（带分页）
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": [
    { "id": 1, "code": "acme", "name": "ACME", "type": "ENTERPRISE", "status": 1 }
  ],
  "limit": 10,
  "offset": 0,
  "total": 1,
  "timestamp": "2026-02-04T12:00:00Z",
  "path": "/api/studio/tenants",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

### 2.2 单对象响应
```json
{
  "status": 200,
  "code": "OK",
  "message": "操作成功",
  "data": { "id": 1, "code": "acme", "name": "ACME", "status": 1 },
  "timestamp": "2026-02-04T12:00:00Z",
  "path": "/api/studio/tenants/1",
  "traceId": "8f6b0c2b0d3f4f2c"
}
```

---

## 3. 数据对象字段（通用）

**Tenant**
- `id, code, name, type, status, level, path, createdAt, updatedAt`

**Role**
- `id, tenantId, type, name, description, status, sort`

**User**
- `id, tenantId, username, email, phone, status`

**RolePermission / UserPermission**
- `objectId, permissionId`

**TenantFeaturePermission**
- `objectId, permissionId, status`

**TenantFeatureAudit**
- `objectId, action, beforeStatus, afterStatus, beforePermissionId, afterPermissionId, operatorUserId, operatorTenantId, createdAt`

**Invitation**
- `id, tenantId, inviteeEmail, inviteeMobile, status, inviteCode, expiresAt, createdAt`

**SsoConfig**
- `id, tenantId, provider, baseUrl, status, configJson`

---

## 4. 租户管理

### 4.1 GET /tenants
Query：`status, limit, offset`

响应 `data` 示例：
```json
[
  { "id": 1, "code": "acme", "name": "ACME", "type": "ENTERPRISE", "status": 1 }
]
```

### 4.2 POST /tenants
请求示例：
```json
{
  "code": "acme",
  "name": "ACME",
  "type": "ENTERPRISE",
  "parentId": null
}
```
响应 `data` 示例：
```json
{ "tenantId": 1 }
```

### 4.3 GET /tenants/{tenantId}
响应 `data` 示例：
```json
{ "id": 1, "code": "acme", "name": "ACME", "type": "ENTERPRISE", "status": 1 }
```

### 4.4 PATCH /tenants/{tenantId}
请求示例：
```json
{ "name": "ACME Group", "type": "ENTERPRISE", "parentId": null }
```
响应 `data`：`null`

### 4.5 PATCH /tenants/{tenantId}/status
请求示例：
```json
{ "status": 0 }
```
响应 `data`：`null`

---

## 5. 产品目录与租户开通（只读 + 分配）

### 5.1 GET /products
响应 `data` 示例：
```json
[
  { "code": "studio", "name": "Studio", "type": "DATA_DEV", "status": 1 }
]
```

### 5.2 GET /products/{productCode}
响应 `data` 示例：
```json
{ "code": "studio", "name": "Studio", "type": "DATA_DEV", "status": 1 }
```

### 5.3 GET /tenants/{tenantId}/products
响应 `data` 示例：
```json
[
  { "productCode": "studio", "status": 1, "startAt": "2026-02-01T00:00:00Z", "endAt": null }
]
```

### 5.4 PUT /tenants/{tenantId}/products
请求示例：
```json
[
  { "productCode": "studio", "status": 1, "startAt": "2026-02-01T00:00:00Z", "endAt": null }
]
```
响应 `data`：`null`

---

## 6. 租户功能授权上限

### 6.1 GET /tenants/{tenantId}/features
Query：`productCode`

响应 `data` 示例：
```json
[
  { "objectId": 101, "permissionId": 2, "status": 1 }
]
```

### 6.2 PUT /tenants/{tenantId}/features
Query：`productCode`  
请求示例：
```json
[
  { "objectId": 101, "permissionId": 2, "status": 1 }
]
```
响应 `data`：`null`

### 6.3 GET /tenants/{tenantId}/feature-audit
Query：`productCode, limit, offset`

响应 `data` 示例：
```json
[
  {
    "objectId": 101,
    "action": "UPDATE_PERMISSION",
    "beforeStatus": 1,
    "afterStatus": 1,
    "beforePermissionId": 1,
    "afterPermissionId": 2,
    "operatorUserId": 9,
    "operatorTenantId": 0,
    "createdAt": "2026-02-04T12:00:00Z"
  }
]
```

---

## 7. 角色与权限（租户内 + 产品维度）

### 7.1 GET /tenants/{tenantId}/roles
Query：`productCode`

响应 `data` 示例：
```json
[
  { "id": 20, "tenantId": 1, "productCode": "studio", "type": "ADMIN", "name": "超级管理员", "status": 1 }
]
```

### 7.2 POST /tenants/{tenantId}/roles
请求示例：
```json
{ "productCode": "studio", "name": "开发者", "type": "USER", "description": "研发工程师", "status": 1 }
```
响应 `data` 示例：
```json
{ "roleId": 21 }
```

### 7.3 PATCH /tenants/{tenantId}/roles/{roleId}
请求示例：
```json
{ "name": "高级开发者", "status": 1 }
```
响应 `data`：`null`

### 7.4 DELETE /tenants/{tenantId}/roles/{roleId}
响应 `data`：`null`

### 7.5 GET /tenants/{tenantId}/roles/{roleId}/permissions
Query：`productCode`

响应 `data` 示例：
```json
[
  { "objectId": 101, "permissionCode": "WRITE" }
]
```

### 7.6 PUT /tenants/{tenantId}/roles/{roleId}/permissions
Query：`productCode`

请求示例：
```json
[
  { "objectId": 101, "permissionId": 2 }
]
```
响应 `data`：`null`

---

## 8. 成员与授权

### 8.1 GET /tenants/{tenantId}/users
Query：`status, limit, offset`

响应 `data` 示例：
```json
[
  { "id": 9, "tenantId": 1, "username": "sunny", "email": "sunny@datapillar.com", "status": 1 }
]
```

### 8.2 说明
成员注册必须通过邀请接口（见第9），平台不提供直接创建用户接口。

### 8.3 PATCH /tenants/{tenantId}/users/{userId}
请求示例：
```json
{ "status": 0 }
```
响应 `data`：`null`

### 8.4 GET /tenants/{tenantId}/users/{userId}/roles
Query：`productCode`

响应 `data` 示例：
```json
[
  { "id": 21, "tenantId": 1, "productCode": "studio", "type": "USER", "name": "开发者", "status": 1 }
]
```

### 8.5 PUT /tenants/{tenantId}/users/{userId}/roles
Query：`productCode`

请求示例：
```json
[21]
```
响应 `data`：`null`

### 8.6 GET /tenants/{tenantId}/users/{userId}/permissions
Query：`productCode`

响应 `data` 示例：
```json
[
  { "objectId": 101, "permissionCode": "WRITE" }
]
```

### 8.7 PUT /tenants/{tenantId}/users/{userId}/permissions
Query：`productCode`

请求示例：
```json
[
  { "objectId": 101, "permissionId": 2 }
]
```
响应 `data`：`null`

---

## 9. 邀请（企业内部邀请员工使用产品）

### 9.1 POST /tenants/{tenantId}/invitations
请求示例：
```json
{
  "productCode": "studio",
  "inviteeEmail": "user@acme.com",
  "inviteeMobile": null,
  "roleIds": [21],
  "expiresAt": "2026-03-01T00:00:00Z"
}
```
约束：
- roleIds 必填且至少 1 个
- 一次邀请只允许一个 productCode
响应 `data` 示例：
```json
{ "invitationId": 1001, "inviteCode": "X9P7-ABCD", "expiresAt": "2026-03-01T00:00:00Z" }
```

### 9.2 GET /tenants/{tenantId}/invitations
Query：`productCode, status, limit, offset`

响应 `data` 示例：
```json
[
  { "id": 1001, "tenantId": 1, "productCode": "studio", "inviteeEmail": "user@acme.com", "status": 0, "inviteCode": "X9P7-ABCD" }
]
```

### 9.3 PATCH /tenants/{tenantId}/invitations/{invitationId}
请求示例：
```json
{ "action": "CANCEL" }
```
响应 `data`：`null`

---

## 10. SSO 配置

### 10.1 GET /tenants/{tenantId}/sso-configs
响应 `data` 示例：
```json
[
  { "id": 88, "tenantId": 1, "provider": "dingtalk", "baseUrl": "https://api.dingtalk.com", "status": 1, "configJson": { "clientId": "xx", "clientSecret": "yy" } }
]
```

### 10.2 POST /tenants/{tenantId}/sso-configs
请求示例：
```json
{
  "provider": "dingtalk",
  "baseUrl": "https://api.dingtalk.com",
  "configJson": { "clientId": "xx", "clientSecret": "yy", "redirectUri": "https://demo.example.com/auth/callback" },
  "status": 1
}
```
响应 `data` 示例：
```json
{ "configId": 88 }
```

### 10.3 PATCH /tenants/{tenantId}/sso-configs/{configId}
请求示例：
```json
{ "baseUrl": "https://api.dingtalk.com", "status": 1 }
```
响应 `data`：`null`
