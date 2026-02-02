# 多租户后端实现文档

> 目标：在现有 Datapillar 架构中落地“租户隔离 + 租户内权限控制”，确保数据/权限/配置按租户隔离。

## 设计原则
- **tenant_id 是硬隔离边界**：所有业务数据与权限授权必须按 tenant_id 过滤。
- **Token 绑定租户**：JWT 必须包含 tenant_id；切换租户必须重新签发 token。
- **用户与租户关系独立**：用户可跨租户，`tenant_users` 作为成员关系与会话载体。
- **组织结构为产品内模型**：当前设计不依赖外部 OA 同步。
- **RBAC 租户化**：角色与授权均按租户分配；权限对象允许 tenant_id=0 全局共享。
- **对标 DataWorks**：身份归一到认证中心（auth/IdP），产品侧只做授权与资源管理，不自建 OA。
- **邀请制入库**：管理员先配组织与角色，用户首次登录绑定身份并落库。
- **邀请优先准入**：未被邀请的用户不能首次入库/登录。

## 对标 DataWorks 的分层
### 1) 身份层（datapillar-auth）
- **统一身份入口**：本地账号 + SSO/OA（可选）统一为内部 `user_id`。
- **身份映射**：第三方账号映射落在 `user_identities`，支持 JIT（首次登录建用户）或同步导入。
- **输出标准身份**：对业务服务只输出 `user_id + tenant_id`。

### 2) 授权层（datapillar-web-admin）
- **产品内 RBAC**：角色/权限/菜单都在产品内按租户与项目（Workspace）授权。
- **不依赖 OA 直连**：当前不接入第三方组织同步，仅使用本地模型和权限体系。

## 数据模型（DDL）
- 统一 DDL：`datapillar-web-admin/docs/db/datapillar_schema.sql`
- 关键表：
  - 租户：`tenants`
  - 用户：`users`（全局身份）
  - 租户成员：`tenant_users`（租户会话 token_sign/token_expire_time 存这里）
  - 组织结构：`orgs`
  - 组织成员：`org_users`
  - 外部身份映射：`user_identities`
  - 租户SSO配置：`tenant_sso_configs`
  - 邀请：`user_invitations/user_invitation_orgs/user_invitation_roles`
  - RBAC：`roles/user_roles/role_permissions/user_permission_overrides/permission_objects/...`
  - 业务表：`projects/job_workflow/job_info/job_dependency/...` 均含 tenant_id

## 认证中心（datapillar-auth）
### 1. 登录
- 登录请求必须携带 `tenant_code`。
- 校验 `tenant_users(tenant_id, user_id)` 是否存在且状态有效。
- 生成 JWT，**claims 必含 tenant_id + user_id**。
- `token_sign/token_expire_time` 写入 `tenant_users`（租户维度会话）。
- 若用户尚未入库（无 `tenant_users`），必须携带邀请码并通过邀请校验后入库。

### 2. 刷新 / 校验
- 解析 token → 读取 tenant_id + user_id。
- 校验 `tenant_users.token_sign` 与 token 签名一致；状态有效。

### 3. 退出
- 清除当前租户的 `tenant_users.token_sign`，只退出当前租户会话。

## SSO 抽象设计（auth）
### 目标
- **只保留 authCode**：统一授权码换用户信息的流程，不再接受 token 直传。
- **新增平台不改接口/表**：平台差异放到 provider 实现与 config_json。
- **租户级配置**：每个租户可独立配置多平台。

### 接口规范
- **扫码配置**：`GET /auth/sso/qr?tenantCode&provider`
  - 返回 `{ type, state, payload }`
  - `type=SDK` 表示前端用 SDK 渲染；`type=URL` 表示直接跳转授权地址。
- **授权码登录**：`POST /auth/sso/login`
  - 请求体：`tenantCode, provider, authCode, state, inviteCode`

### Provider 抽象
- `SsoProvider` 接口：
  - `provider()`：返回 `dingtalk/wecom/feishu/lark`
  - `buildQr(config, state)`：生成扫码配置或授权URL
  - `exchangeCode(config, authCode)`：授权码换取用户 token
  - `fetchUserInfo(config, token)`：获取用户信息
- `SsoProviderRegistry`：按 `provider` 路由实现
- `SsoProviderConfig`：从 `tenant_sso_configs.config_json` 反序列化并校验必填项

### 代码目录与接口位置（datapillar-auth）
```
com.sunny.datapillar.auth.sso
├─ SsoProvider                // Provider 抽象
├─ SsoProviderRegistry        // Provider 路由
├─ SsoConfigService           // 读取/校验 tenant_sso_configs
├─ SsoQrService               // 生成扫码配置
├─ SsoAuthService             // authCode 登录入口
├─ model
│  ├─ SsoQrResponse
│  ├─ SsoToken                // provider 级 token
│  ├─ SsoUserInfo             // 统一用户信息结构
│  └─ SsoProviderConfig       // config_json 映射
└─ provider
   ├─ DingtalkSsoProvider
   ├─ FeishuSsoProvider
   └─ WecomSsoProvider
```

- 请求 DTO：`AuthDto.SsoLoginRequest`（tenantCode/provider/authCode/state/inviteCode）

### 统一用户信息
- 统一结构 `SsoUserInfo`：`externalUserId/unionId/openId/mobile/email/nick/corpId/rawJson`
- `externalUserId` 规则：优先 `unionId`，其次 `openId`，再退化为平台 `userId`
- 基础字段写入 `user_identities`；扩展字段写入 `profile_json`

### 租户级配置（tenant_sso_configs）
- `base_url`：区分环境（如飞书/Lark）或平台域名
- `config_json` 建议结构：
  - `clientId/clientSecret/redirectUri/scope/corpId/responseType/prompt`
- 钉钉示例：
```json
{
  "clientId": "dingxxx",
  "clientSecret": "xxxxx",
  "redirectUri": "https://demo.example.com/auth/callback",
  "scope": "openid corpid",
  "responseType": "code",
  "prompt": "consent",
  "corpId": "dingcorp"
}
```

### 扫码状态（state）存储
- **必须服务端生成并校验**，防重放与串号
- **采用 Redis**：`state -> tenantId/provider/expireAt`（TTL 自动过期）

## 网关（datapillar-api-gateway）
- **只信 JWT**：从 token 解析 tenant_id/user_id。
- 将租户上下文注入请求头（建议沿用公共常量）：
  - `X-Tenant-Id`（新增）
  - `X-User-Id`、`X-Username`（已有）
- 禁止客户端自带 `X-Tenant-Id` 覆盖。

## 业务服务（datapillar-web-admin 等）
### 1. 租户上下文
- 统一 `TenantContext`（ThreadLocal/MDC），从请求头解析租户信息。
- 所有日志、审计打点包含 tenant_id。

### 2. 数据访问层
- 所有查询/更新/删除必须强制携带 `tenant_id`。
- 推荐方案：MyBatis-Plus 租户插件（统一注入）；或 Mapper 手动加条件（更显式）。

### 3. RBAC
- `roles/user_roles/role_permissions/user_permission_overrides` 全部按 tenant_id 过滤。
- `permission_objects/permission_object_categories/permissions` 支持 tenant_id=0 全局数据 + 租户覆盖。

### 4. 业务表
- `projects/job_workflow/job_info/job_dependency/job_component/knowledge_*` 等表必须按 tenant_id 隔离。

### 5. 用户/租户接口
- 在 **web-admin** 提供 `GET /users/{id}/tenants`：返回用户可访问租户列表。
- `{id}` 必须与 token 中 `user_id` 一致，不一致直接拒绝。
- 切换租户 = 重新签发 token。

## 邀请制入库（推荐）
### 核心规则
- **邀请优先**：首次入库必须携带 `invite_code`，否则拒绝。
- **匹配方式**：邮箱或手机号均可作为邀请对象；登录时以 `invite_code` 为准入凭证。
- **账号策略**：允许本地账号 + SSO/OA 登录共存。
- **组织归属**：允许多组织，一个用户可绑定多个 `orgs`。
- **唯一标识**：用 `invitee_key` 归一化邮箱/手机号（如邮箱小写、手机号 E.164），用于去重。

### 流程
1) 管理员创建邀请：选择租户、组织、角色；填写邮箱/手机号；生成邀请码。
2) 用户首次登录：通过邀请链接携带 `invite_code`，再完成本地账号或 SSO/OA 认证。
3) 绑定与落库：
   - 若用户不存在，创建 `users`；
   - 写入 `tenant_users`（成员关系）；
   - 写入 `org_users`（多组织归属）；
   - 写入 `user_roles`（角色授权）；
   - 若为 SSO/OA，写入 `user_identities`（外部身份映射）。

### 约束与校验
- 邀请码一次性使用；过期不可用。
- 邀请中的租户必须与登录时选择的租户一致。
- 邀请中的组织/角色必须属于该租户。
- 同一租户同一邀请对象**同一时间仅允许一条待接受邀请**；通过 `active_invitee_key` 约束（待接受时写入，其他状态置空）。
- 若邀请填写了邮箱/手机号，SSO/OA 回调的已验证邮箱/手机号必须匹配，否则拒绝。

## 跨租户管理员（可选）
- 若需要集团级跨租户访问：
  - 方案A：设置“平台管理员”角色，放宽租户过滤。
  - 方案B：建立“系统租户”，平台账号只在系统租户中操作。

## 最小落地路径
1) Token 增加 tenant_id，`tenant_users` 存会话签名。
2) Gateway 注入 `X-Tenant-Id`，下游只信 token。
3) 数据层强制 tenant_id 过滤。
4) RBAC 租户化。
5) 邀请制入库 + 组织结构与成员关系按产品内规则维护。
