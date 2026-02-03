# 平台与产品服务拆分方案（业界口径）

本文档用于明确 Datapillar 平台级多租户与产品服务的边界，防止后续能力揉在一起。

## 1. 目标与原则

**目标**
- 多产品共用同一套租户体系（平台级多租户）
- 平台与产品服务解耦，边界清晰
- 先完成服务边界，再进行数据库物理拆分

**原则**
- 租户体系只维护一份（平台真源）
- 产品服务禁止直连平台库，只通过平台 API
- 所有产品业务表必须带 `tenant_id` 进行隔离
- 菜单/功能/权限资源统一在平台注册

---

## 2. 服务边界（业界主流）

### 2.1 平台服务（datapillar-platform）
跨产品共享能力，平台级真源：
- 租户/工作空间
- 用户/角色/权限/IAM
- 菜单/功能对象注册
- 套餐/功能包/授权
- 配额与使用量
- 审计日志
- SSO 配置 / API Key
- 计费能力（可后置，但结构预留）

### 2.2 产品服务（datapillar-studio-service）
仅包含 Studio 业务能力：
- 组织/部门/成员（产品内）
- 数据目录/血缘/质量规则
- 项目/工作流/作业/组件
- SQL IDE/发布流程
- 知识库与 AI 用量
- 所有业务表必须带 `tenant_id`

### 2.3 认证服务（datapillar-auth）
- 仅负责登录、签发与刷新 Token
- 权限/套餐/配额判定交由平台服务

---

## 3. 关键规则（避免再次揉在一起）
- 平台库只允许平台服务访问
- 产品服务不直连平台库，统一走平台 API
- 产品服务只关心 `tenant_id`
- 权限/菜单/功能对象统一在平台注册
- Token 会话由认证服务统一管理（数据库或 Redis），平台库不持久化 token

---

## 4. 拆分顺序（推荐）

**阶段 1：服务拆分（逻辑隔离）**
1. 现有核心业务服务拆成：
   - `datapillar-platform`（平台服务）
   - `datapillar-studio-service`（Studio 服务）
2. 网关路由分流：
   - `/api/platform/**` → platform
   - `/api/studio/**` → studio-service
3. Studio 业务禁止访问平台表，只能调用平台 API

**阶段 2：数据库拆分（物理隔离）**
1. 新建独立库：
   - `datapillar_platform`
   - `datapillar_studio`
2. 平台表迁移到平台库，Studio 表迁移到业务库

---

## 5. 表归属（基于现有 datapillar_schema.sql）

### 5.1 平台库（datapillar_platform）
**租户与成员**
- tenants
- users / tenant_users
- user_identities
- tenant_sso_configs

**IAM 与权限**
- roles / permissions
- feature_object_categories / feature_objects
- user_roles / role_permissions / user_permission_overrides
- user_invitations / user_invitation_orgs / user_invitation_roles
- tenant_feature_permissions / tenant_feature_audit

**套餐与配额（需新增）**
- plan / plan_feature / plan_quota
- tenant_subscription
- tenant_quota_override
- quota_usage

### 5.2 Studio 库（datapillar_studio）
- orgs / org_users
- projects
- job_workflow / job_component / job_info / job_dependency
- knowledge_namespace / knowledge_document / knowledge_document_job
- ai_llm_usage

---

## 6. 平台 API 消费方式（产品服务）
产品服务只通过平台 API 获取“可用能力”：
- 租户信息 / 状态
- 角色权限 / 菜单授权
- 套餐功能 / 配额额度
- 配额使用量

**性能建议**
- 平台返回 `entitlementVersion`
- Studio 服务做本地缓存（tenantId + version）
- 版本变化时平台通知或定期拉取

### 6.1 平台 API 列表（RESTful，统一 ApiResponse）
**统一规范**
- 前缀：`/api/platform`
- 所有响应使用 `ApiResponse<T>` 包装（`status/code/message/data/timestamp/traceId`）
- 列表接口：`data` 为数组，分页使用 `limit/offset/total`

**租户与组织**
- `GET /tenants`：租户列表（支持 `limit/offset/status`）
- `POST /tenants`：创建租户
- `GET /tenants/{tenantId}`：租户详情
- `PATCH /tenants/{tenantId}`：更新租户基础信息
- `PATCH /tenants/{tenantId}/status`：启用/禁用/冻结
- `GET /tenants/{tenantId}/orgs`：组织树
- `POST /tenants/{tenantId}/orgs`：新增组织
- `PATCH /tenants/{tenantId}/orgs/{orgId}`：更新组织

**用户与成员**
- `GET /tenants/{tenantId}/users`：租户成员列表
- `POST /tenants/{tenantId}/users`：创建/添加成员
- `PATCH /tenants/{tenantId}/users/{userId}`：更新成员状态
- `GET /users/{userId}`：用户详情（平台视角）

**邀请**
- `POST /tenants/{tenantId}/invitations`：发起邀请
- `GET /tenants/{tenantId}/invitations`：邀请列表
- `PATCH /tenants/{tenantId}/invitations/{invitationId}`：取消/重发邀请

**角色与权限**
- `GET /tenants/{tenantId}/roles`：角色列表
- `POST /tenants/{tenantId}/roles`：创建角色
- `PATCH /tenants/{tenantId}/roles/{roleId}`：更新角色
- `DELETE /tenants/{tenantId}/roles/{roleId}`：删除角色
- `GET /tenants/{tenantId}/roles/{roleId}/permissions`：角色权限矩阵
- `PUT /tenants/{tenantId}/roles/{roleId}/permissions`：批量更新角色权限

**功能对象与菜单**
- `GET /feature-categories`：功能分类（系统级）
- `GET /feature-objects`：功能对象列表（系统级）
- `POST /feature-objects`：注册功能对象（系统级）
- `PATCH /feature-objects/{objectId}`：更新功能对象（系统级）
- `GET /tenants/{tenantId}/menus`：租户菜单（含授权过滤）

**产品注册与能力清单**
- `GET /products`：产品列表
- `POST /products`：注册产品
- `GET /products/{productCode}`：产品详情
- `GET /products/{productCode}/features`：产品功能点
- `POST /products/{productCode}/features`：新增功能点
- `GET /products/{productCode}/permissions`：产品权限点
- `POST /products/{productCode}/permissions`：新增权限点

**授权与套餐能力**
- `GET /tenants/{tenantId}/entitlements`：租户授权快照（支持 `product=studio`）
- `PUT /tenants/{tenantId}/entitlements`：更新租户授权（平台超管）

**配额与使用量**
- `GET /tenants/{tenantId}/quotas`：租户配额
- `PUT /tenants/{tenantId}/quotas`：更新租户配额
- `GET /tenants/{tenantId}/quota-usage`：配额使用量

**SSO 与身份**
- `GET /tenants/{tenantId}/sso-configs`：SSO 配置列表
- `POST /tenants/{tenantId}/sso-configs`：新增 SSO 配置
- `PATCH /tenants/{tenantId}/sso-configs/{configId}`：更新 SSO 配置

**审计**
- `GET /tenants/{tenantId}/audit-logs`：租户审计日志

---

## 7. 迁移方式（默认节奏）

**采用方式 A（推荐）**
1. 先拆服务与路由
2. 业务访问平台能力全部改为 API
3. 再拆库迁移表

---

## 8. 风险与控制
- 风险：权限体系混乱 → 控制：权限资源统一在平台登记
- 风险：跨服务数据耦合 → 控制：禁止直连平台库
- 风险：套餐/配额与业务脱节 → 控制：产品服务运行时向平台校验

---

## 9. 待确认事项
1. 平台服务拆分完成后是否更名（当前使用 `datapillar-platform`）
2. 套餐/配额的最小可用字段清单（设计套餐时确认）
