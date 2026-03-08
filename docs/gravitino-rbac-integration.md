# Datapillar Studio-Service 与 Gravitino RBAC 打通方案

## 1. 背景

当前 Datapillar 与 Gravitino 的打通存在明显结构性问题：

1. Gravitino 调用分散在 `setup`、`tenant`、`user`、`role`、`governance` 多个服务中，边界混乱，顺序失控。
2. Studio 侧当前只打通了 `role -> privileges`，没有真正打通 `user -> role binding`，导致本地 RBAC 与 Gravitino RBAC 不是同一套模型。
3. 代码与接口命名里存在 `sync` 语义，这不对。`studio-service` 既然作为 Gravitino 的前端，就应该执行明确的 CRUD / command，而不是做“同步器”。
4. 产品形态要求管理员既能把某个用户加入某个角色获得数据权限，也能对单个用户做附加数据权限调整；如果直接引入“用户直授权限”第二套模型，会把权限解释彻底搞烂。

本方案目标只有一个：

**把 Datapillar 的数据权限能力统一收敛到一套以 Gravitino RBAC 为底座的访问控制模型。**

---

## 2. 结论

### 2.1 必须采用的主模型

Datapillar 的数据权限必须采用如下主模型：

```text
Studio user / role / membership
    -> studio-service access control command
    -> Gravitino user / role / role privilege / user-role binding
```

即：

1. **Studio RBAC 与 Gravitino RBAC 全量打通**。
2. **数据权限只通过 Gravitino role privilege 实现**。
3. **用户获得数据权限的方式必须是“绑定角色”**，不是单独发一套用户权限。

### 2.2 用户单独数据权限的正确做法

产品允许“对某个用户单独调数据权限”，但底层不应该做成 `user direct privilege`。

正确做法：

1. 为该用户维护一个隐藏的“个人附加角色”。
2. 该角色只绑定给该用户。
3. 用户附加数据权限写到这个附加角色上。

例如：

```text
base role: analyst
user override role: user_override_123

最终权限 = analyst privileges + user_override_123 privileges
```

这样既满足产品形态，又不破坏 Gravitino 原生 RBAC 模型。

---

## 3. 强制设计原则

1. **数据权限唯一底座是 Gravitino RBAC**，禁止并行维护第二套用户直授权限模型。
2. **Studio 是 Gravitino 的前端，不是同步器**。所有写操作使用明确命令语义：`create/get/delete/replace/grant/revoke`。
3. **所有 Gravitino 调用必须收口**，业务服务禁止直接调用分散的 client。
4. **本地用户/角色/成员关系是 Studio 主数据**。
5. **本地数据对象权限结果以 Gravitino 为准**。
6. **用户附加数据权限必须通过“用户附加角色”实现**。
7. **`role_code` 必须是远端稳定主键**，`display_name` 只能做展示，不得直接作为 Gravitino role name 的可变来源。
8. **`external_user_id` 固定为 Studio `users.id`**。
9. **`X-External-User-Id` 专门用于写入 Gravitino `user_meta.external_user_id`**，禁止再混用 `X-User-Id`。
10. **前后端接口契约必须最小改动**。优先改路径，不重做请求/响应结构；当前用户语义继续来自网关注入的可信头，而不是前端自传。
11. **`studio-service` 主要承担 Gravitino 前端职责**：路由、鉴权、租户上下文、参数校验、ID 映射、错误映射；真正的访问控制模型修复必须落在 Gravitino 侧。
12. **`studio-service` 集成 Gravitino 必须使用官方 `java-client`**，禁止在 `studio-service` 再自研一层 path / method / `JsonNode` 驱动的 transport façade。
13. **禁止出现伪 SDK 设计**：`routeRequest(...)`、`executeMetadataRoute(...)`、`executeSemanticRoute(...)`、按 URL segment 分发的通用路由器、`JsonNode` 泛型透传，都属于必须退场的自研 façade。
14. **若 `java-client` 缺能力，必须先补 `datapillar-gravitino/clients/client-java` 的 typed client / object wrapper，再由 `studio-service` 调用**；禁止把缺口转嫁成 `studio-service` 自己维护的 transport façade。

---

## 4. 当前问题清单

### 4.1 调用分散

当前 Gravitino 调用散落在：

1. `SetupServiceImpl`
2. `TenantServiceImpl`
3. `GovernanceMetadataServiceImpl`
4. `GovernanceSemanticServiceImpl`
5. `UserServiceImpl`
6. `RoleServiceImpl`

这会导致：

1. 相同语义在多个业务服务里重复实现。
2. 调用顺序和补偿逻辑难以统一。
3. 一旦补“用户绑定角色”“用户附加数据权限”，复杂度会继续指数上升。

### 4.2 权限模型未闭环

当前已经存在：

1. `role -> Gravitino role privilege` 的读取与替换。

但当前缺失：

1. `user -> Gravitino role binding`。
2. `role delete -> Gravitino role delete / revoke bindings`。
3. `user disable / offboard -> revoke all user roles`。
4. `用户附加数据权限 -> 用户附加角色` 模型。

所以目前不是完整 RBAC 打通，只是权限面打通了一半。

### 4.3 接口命名错误

当前 `sync role data privileges` 这种命名是错误的。

正确语义应该是：

1. `replaceRolePrivileges`
2. `listRolePrivileges`
3. `replaceUserRoles`
4. `replaceUserOverridePrivileges`

### 4.4 语义资产 owner 语义缺失

当前语义资产链路存在一个更隐蔽但更关键的问题：

1. `audit.creator` 只能表达创建者，不等于 owner。
2. owner 真正来自 Gravitino 的 `owner_meta` 关系。
3. 当前代码已具备语义资产 owner 自动写入链路，`wordroot / metric / unit / modifier / value-domain` 不能再被视为天然缺失 owner hook；风险点在于 owner 权限语义和对象模型一致性，而不是拿 `audit.creator` 兜底。

这会导致：

1. 前端创建了一个指标，返回里能看到 `audit.creator`，但不代表 owner 已建立。
2. 前端如果需要 owner，不能从 `audit.creator` 推断。
3. 若语义资产 owner 没有在 Gravitino 侧写入，`studio-service` 无法通过本地筛选弥补这个语义缺口。

结论：

1. **语义资产 owner 仍然必须由 Gravitino 原生 owner 模型负责；当前优先事项不是让 Studio 用 `audit.creator` 兜底，而是把已存在的 owner 能力收敛回原生 dispatcher decorator 规范。**

### 4.5 自研 transport façade 问题（P0）

当前 `studio-service` 的 Gravitino 集成层存在明显跑偏：

1. `GravitinoMetadataClient` / `GravitinoSemanticClient` 暴露大量 `JsonNode` 接口。
2. 内部通过 `routeRequest(...)`、`executeMetadataRoute(...)`、`executeSemanticRoute(...)`、path segment switch 重新解释 Gravitino 资源语义。
3. 这本质上是在 `studio-service` 内部自研一套弱化版 Gravitino transport façade，而不是复用官方 `java-client`。

这会直接带来：

1. Java 类型系统失效，调用边界退化成字符串 + `JsonNode`。
2. REST path 规则、对象类型解析、请求体拼装散落在 `studio-service`，长期一定失控。
3. 一旦 Gravitino Java client 或 REST 语义调整，`studio-service` 会多维护一套伪协议层。
4. `studio-service` 作为 Gravitino 的外部 Java 调用方，没有理由放着官方 `java-client` 不用，反而再造 transport façade。

结论：

1. **必须禁止 `studio-service` 自研 transport façade。**
2. **`integration/gravitino/*` 必须收敛为官方 `java-client` 适配层，而不是通用 path router。**
3. **若现有 `java-client` 缺少 typed client，必须先回到 `datapillar-gravitino/clients/client-java` 补齐，再让 `studio-service` 调用。**

### 4.6 `catalog-dataset` 语义资产能力扫描结果

基于当前 `datapillar-gravitino/catalogs/catalog-dataset` 与 server / api / common 代码扫描，当前语义资产至少存在以下原生能力缺口。

#### A. owner 体系现状（P0）

1. `MetadataObject.Type` 已包含 `METRIC / WORDROOT / UNIT / MODIFIER / VALUE_DOMAIN`。
2. `OwnerOperations` 已可基于 `MetadataObject.Type` 与 `MetadataObjects.parse(...)` 处理这些语义资产对象。
3. `catalog-dataset` 创建链路已接入 owner 自动写入；owner 真实来源仍然是 Gravitino 的 `owner_meta` 关系，而不是 `audit.creator`。
4. `modifier` 底层对象类型已统一为 `Entity.EntityType.MODIFIER`，底层表已统一为 `modifier_meta`，不再保留 `METRIC_MODIFIER / metric_modifier_meta` 这套命名。

直接后果：

1. `getOwner(metric/wordroot/unit/modifier/value-domain)` 应视为原生 owner 能力，必须通过 owner 接口获取。
2. `audit.creator` 仍然不能替代 owner。

#### B. 访问控制对象类型现状（P0）

1. 语义资产已进入统一 `MetadataObject.Type` / `Entity.EntityType` / authorization expression 链路。
2. `modifier` 的底层 entity 命名已从 `METRIC_MODIFIER` 收敛为 `MODIFIER`，避免继续在 RBAC / owner / object operation 中做额外映射补丁。

直接后果：

1. 语义资产数据权限可以按 schema-child 对象统一进入原生元数据对象模型。
2. `role -> privilege -> semantic object` 这条链不再因为 `modifier` 命名分裂而失真。

#### C. 返回契约缺口（P1）

1. 当前语义资产 DTO 普遍只返回业务字段 + `audit`。
2. 返回 DTO 不带 `owner`。
3. 原生 owner 又是独立接口。

直接后果：

1. detail 页面若要 owner，当前只能做额外 owner 查询。
2. list 页面若也需要 owner，直接会落入 N+1。

#### D. 通用对象能力现状（P1）

语义资产已经进入统一 metadata object 模型：

1. owner / role / tag / policy 这类基于 metadata object 的通用能力可以复用原生链路。
2. 当前剩余问题主要是返回契约和上层调用方式，而不是对象模型缺位。

#### E. 当前可确认的状态统计

当前扫描可直接确认：

1. **`metric / wordroot / unit / modifier / value-domain` 已进入统一 metadata object / owner / authorization 模型。**
2. **`modifier` 底层命名已统一为 `MODIFIER`，底层表名已统一为 `modifier_meta`。**
3. **当前语义资产返回 DTO 默认仍不带 owner。**
4. **owner 查询必须继续走独立 owner 接口，不能从 `audit.creator` 推断。**

#### F. dataset dispatcher 收敛结果（P0）

当前 `catalog-dataset` 已按原生 Gravitino dispatcher decorator 规范完成第一阶段收敛：

1. `DatasetEventDispatcher -> DatasetNormalizeDispatcher -> DatasetHookDispatcher -> DatasetOperationDispatcher` 已落地。
2. `DatasetNormalizeDispatcher` 当前保持薄层 pass-through，先把 decorator 形状立住。
3. `DatasetHookDispatcher` 已统一承接 create 前 current user 校验、create 后 owner 绑定、owner 失败回滚、delete 后 authorization plugin privilege 清理。
4. `DatasetOperationDispatcher` 已收敛为 tree lock、catalog 路由、异常翻译和纯调度，不再继续承载 owner / authorization side effect。

本阶段边界：

1. 作用对象只覆盖 `metric / modifier / wordroot / unit / value-domain`。
2. 不动 Studio 侧调用契约。
3. 不重命名外部 public dataset API（例如 `getMetricModifier / createMetricModifier` 这类入口）。
4. 不把 `metric version` 混进这次 dispatcher / hook 收敛。

#### G. 设计结论

1. 语义资产已经进入统一对象模型与权限链路，但 owner 返回契约和上层调用方式仍未完全收口。
2. `modifier` 必须保持通用对象定位，底层不得再回退到 `METRIC_MODIFIER / metric_modifier_meta` 这类分裂命名。
3. `studio-service` 仍必须通过原生 owner 接口取 owner，禁止从 `audit.creator` 推断。
4. `catalog-dataset` 已收敛为 `Event -> Normalize -> Hook -> Operation`，`DatasetOperationDispatcher` 只保留调度职责。

---

## 5. 目标分层

## 5.1 Studio 本地职责

Studio 本地保留：

1. 用户管理
2. 角色管理
3. 用户与角色的成员关系
4. 平台功能权限（菜单、页面、按钮）
5. 邀请、租户、成员状态等产品域信息
6. 作为 Gravitino 前端，对外暴露统一接口路径，但不重新发明一套新的前后端契约

## 5.2 Gravitino 职责

Gravitino 负责：

1. 数据对象的 owner
2. role privilege
3. user-role binding
4. 通过 RBAC 对 catalog / schema / table / column / semantic object 做授权控制
5. 访问控制源语义修复（user metadata、role membership、owner 检查、RBAC 行为）

---

## 6. 收口后的调用架构与目录设计

上一版如果直接新造 `facade / command / query / gateway / support` 深层目录，会违背当前 `studio-service` 的现有开发规范。

当前仓库的稳定组织方式是：

1. 业务按 `module/*`
2. DTO 按 `dto/*`
3. 外部系统调用按 `integration/*`

所以 Gravitino 重构必须贴现有规范，而不是另起一套新框架。

## 6.1 目标目录（符合当前规范）

```text
datapillar-studio-service/src/main/java/com/sunny/datapillar/studio
├── dto
│   ├── tenant
│   ├── user
│   ├── metadata        ← controller-facing metadata DTO，已完成 request / response 拆分
│   └── semantic        ← controller-facing semantic DTO，已完成 request / response 拆分
│
├── integration
│   └── gravitino
│       ├── GravitinoClientFactory.java
│       ├── GravitinoExceptionMapper.java
│       ├── GravitinoAdminOpsClient.java
│       ├── GravitinoMetadataClient.java
│       ├── GravitinoSemanticClient.java
│       ├── model       ← integration internal model / command
│       └── service
│           ├── GravitinoMetalakeService.java
│           ├── GravitinoCatalogService.java
│           ├── GravitinoSchemaService.java
│           ├── GravitinoTableService.java
│           ├── GravitinoTagService.java
│           ├── GravitinoOwnerService.java
│           ├── GravitinoMetricService.java
│           ├── GravitinoWordRootService.java
│           ├── GravitinoUnitService.java
│           ├── GravitinoModifierService.java
│           ├── GravitinoValueDomainService.java
│           ├── GravitinoRoleService.java
│           ├── GravitinoRolePrivilegeService.java
│           ├── GravitinoUserService.java
│           ├── GravitinoUserRoleService.java
│           ├── GravitinoUserDataPrivilegeService.java
│           └── GravitinoSetupService.java
│
└── module
    ├── metadata/controller/MetadataBizController.java
    ├── metadata/service/MetadataBizService.java
    ├── metadata/mapper/MetadataDtoMapper.java
    ├── semantic/controller/SemanticBizController.java
    ├── semantic/service/SemanticBizService.java
    ├── semantic/mapper/SemanticDtoMapper.java
    ├── tenant/controller/TenantRoleAdminController.java
    ├── tenant/controller/TenantMemberAdminController.java
    ├── tenant/service/impl/TenantServiceImpl.java
    ├── user/service/impl/UserServiceImpl.java
    └── user/service/impl/RoleServiceImpl.java
```

## 6.2 目录职责

### DTO 约束

当前确认后的规则已经收敛成下面这套：

1. **DTO 是 Controller 层契约，不是 integration 层契约。**
2. **Controller-facing DTO 必须按业务模块拆分**，最终应落到 `dto/metadata`、`dto/semantic`、`dto/tenant`、`dto/user` 这类目录；`dto/gravitino/*` 已删除，禁止重新引入。
3. **integration 层内部模型已经迁入 `integration/gravitino/model/*`，Controller 层不再依赖旧的 Gravitino DTO 目录。**
4. 请求 DTO 已按 `dto/metadata/request`、`dto/semantic/request` 拆分；integration service 对外改为 typed command，不再暴露 `JsonNode`。
5. **前端接口里禁止暴露 `metalake` 概念**，避免把 Gravitino 控制面概念直接泄漏给产品层。

### `integration/gravitino/*`

`integration/gravitino` 的定位现在已经明确：

1. 这里只放 **官方 `java-client` 适配层** 与 **资源粒度 service**。
2. service 必须按资源拆开，禁止再回到一个总入口：
   - `GravitinoCatalogService`
   - `GravitinoSchemaService`
   - `GravitinoTableService`
   - `GravitinoMetricService`
   - `GravitinoUserService`
   - `GravitinoUserRoleService`
   - `GravitinoRolePrivilegeService`
   - `GravitinoUserDataPrivilegeService`
   - `GravitinoSetupService`
3. `integration/gravitino` 允许做：client 创建、typed 调用封装、异常映射、少量上下文转换。
4. `integration/gravitino` 不允许做：通用 path router、`routeRequest(...)`、`executeMetadataRoute(...)`、`executeSemanticRoute(...)`、统一 gateway 大杂烩。
5. 业务模块可以依赖 **资源粒度的 integration service**，但禁止直接依赖底层 client。

### `module/*/controller` 与 `module/*/service`

确认后的 Studio 落地方式不是新增 `module/gravitino/controller/*`，而是：

1. **管理接口继续挂在业务模块 Controller 上**，例如租户成员、租户角色。
2. **业务资源接口继续挂在业务模块 Controller 上**，例如 metadata、semantic。
3. 前端调用应该面对一个业务入口，不应该为了 Gravitino 再请求第二个 Controller。
4. `module/gravitino/controller/*` 已经删除，后续禁止恢复。

## 6.3 已收敛的服务与控制器

当前 Studio 侧已经落地为下面这套形态：

1. `SetupServiceImpl` -> `GravitinoSetupService`
2. `TenantServiceImpl` -> `GravitinoMetalakeService / GravitinoCatalogService / GravitinoSchemaService`
3. `UserServiceImpl` -> `GravitinoUserService / GravitinoUserRoleService / GravitinoUserDataPrivilegeService`
4. `RoleServiceImpl` -> `GravitinoRoleService / GravitinoRolePrivilegeService / GravitinoUserRoleService`
5. `MetadataBizController` -> 元数据业务前端接口
6. `SemanticBizController` -> 语义资产业务前端接口
7. `TenantRoleAdminController` / `TenantMemberAdminController` -> 数据权限管理接口

## 6.4 已删除的错误抽象

下面这些抽象已经被判定为错误方向，当前方案中禁止继续存在：

1. `module/gravitino/controller/*`
2. `GravitinoAccessService`
3. `GravitinoMetadataService`
4. `GravitinoSemanticService`
5. 任何 `GatewayService` / `FacadeService` 风格的 Gravitino 大总线

## 6.5 当前已完成的 Studio 侧收敛

当前 Studio 侧已经完成这轮核心收敛：

1. **controller-facing DTO 已拆回 `dto/metadata/*` 与 `dto/semantic/*`，原 `dto/gravitino/*` 目录已删除。**
2. **`MetadataBizController` / `SemanticBizController` 已改为只接受业务 request DTO，不再把 `JsonNode` 作为 Controller 契约。**
3. **integration service 已改为 typed command / internal model，不再对上暴露 `JsonNode` 或 controller DTO。**

1. 不允许新增比 `module/* -> service -> impl` 更深的通用分层目录。
2. 不允许把 Gravitino DTO 继续散落在 `dto/tenant` / `dto/user`。
3. 不允许业务 service 直接 new client 或直接调用 `integration/gravitino/*Client`。
4. 不允许继续把 Gravitino 逻辑混在 `governance`、`user`、`tenant` 业务实现里。
5. 不允许在 `studio-service` 维护 path / method / `JsonNode` 驱动的自研 transport façade。
6. 不允许再新增 `routeRequest(...)` / `execute*Route(...)` / URL segment switch 这种通用路由器。
7. 若 Gravitino SDK 缺能力，必须优先回补 `datapillar-gravitino/clients/client-java`，而不是在 `studio-service` 伪造第二套客户端。

---

## 7. 数据模型约束

## 7.1 用户

Studio 用户与 Gravitino 用户的对应关系固定为：

1. `user_name = Studio username`
2. `external_user_id = Studio users.id`

`external_user_id` 的写入头固定为：

1. `X-External-User-Id`

其中：

1. 普通业务用户写真实 `users.id`
2. `datapillar` 服务账号固定写 `-1`

## 7.2 角色

角色必须拆分为：

1. `role_code`：不可变，作为 Gravitino role name
2. `display_name`：前端展示名，可变

如果现阶段不改表结构，则必须先执行临时策略：

1. 禁止修改 role 的远端标识名。

## 7.3 用户附加角色

为支持用户附加数据权限，定义隐藏角色命名规范：

```text
user_override_{userId}
```

该角色：

1. 只绑定一个用户
2. 不在普通角色列表直接展示
3. 只用于承载该用户的附加数据权限

---

## 8. 接口规划

**接口域必须统一，但统一的是业务语义，不是伪造一个 `/gravitino` 前缀。**

当前已经确认：

1. `/biz/governance/**` 是旧治理入口，后续不再扩写。
2. 数据权限管理归属 `tenant` 业务域，不应该被强行挪到一个伪控制面前缀下。
3. 元数据与语义资产归属各自业务域，不应该再额外套一层 `/gravitino` 前缀。

因此当前正确做法是：

1. 管理接口继续归属真实业务域，例如 `tenant role`、`tenant member`。
2. 业务资源接口继续归属真实业务域，例如 `metadata`、`semantic`。
3. `studio-service` 负责把业务接口路由到 Gravitino，而不是把 Gravitino 控制面概念直接暴露给前端。

`studio-service` 的职责仍然包括：

1. 身份透传
2. 多租户上下文绑定
3. 本地主数据校验
4. 本地 ID 与远端标识映射
5. 统一错误映射

所以外部接口命名应该尽量保持 **Gravitino 原生资源层级**。

## 8.0 接口契约原则（强制）

接口路径可以重构，但**请求/响应契约不能大改**。

强制原则：

1. 前端原则上只改请求地址，不整体重写参数结构。
2. 当前已有请求体模型能复用就复用，例如：
   - 角色数据权限继续复用 `RoleDataPrivilegeSyncRequest`
   - 角色数据权限项继续复用 `RoleDataPrivilegeCommandItem`
   - 现有治理对象 JSON body 能复用就继续复用
3. 当前用户身份不由前端传入，继续由网关注入可信头：
   - `X-User-Id`
   - `X-Username`
   - `X-Tenant-Id`
   - `X-Tenant-Code`
4. `X-External-User-Id` 不是前端协议，是 `studio-service -> Gravitino` 内部协议。
5. 管理员操作某个目标用户时：
   - 目标用户仍由 path/body 中现有字段表达
   - 当前操作者仍只信网关头
6. 除非 Gravitino 侧 API 要求新增不可避免字段，否则不新增前端字段。

## 8.1 统一外部接口域

当前对前端暴露的外部接口域已经收敛为：

1. 业务元数据接口：`/biz/metadata/**`
2. 业务语义接口：`/biz/semantic/**`
3. 租户角色数据权限接口：`/admin/tenant/current/roles/{roleId}/data-privileges`
4. 租户成员数据权限接口：`/admin/tenant/current/members/{memberId}/data-privileges`

这里有两个强约束：

1. **不再对前端暴露 `/admin/gravitino/**`、`/biz/gravitino/**` 这一层统一前缀。**
2. **不再对前端暴露 `metalake` 参数。**

## 8.2 命名原则

1. 路径命名优先服从产品语义，而不是 Gravitino 控制面语义。
2. 管理动作应挂在拥有该业务语义的模块下，例如 `tenant role`、`tenant member`。
3. 业务资源应挂在实际业务域下，例如 `metadata`、`semantic`。
4. `studio-service` 内部仍然可以做 domain / metalake 路由，但这种概念不能外泄到前端契约。

## 8.3 当前已落地接口形态

### 8.3.1 元数据业务接口（`/biz/metadata/**`）

当前已落地：

1. `catalog`：list / create / load / update / delete
2. `schema`：list / create / load / update / delete
3. `table`：list / create / load / update / delete
4. `tag`：list / create / load / update / delete
5. `owner`：get

### 8.3.2 语义业务接口（`/biz/semantic/**`）

当前已落地：

1. `wordroots`：list / create / load / update / delete
2. `metrics`：list / create / load / update / delete
3. `metric versions`：list / load / update / switch
4. `units`：list / create / load / update / delete
5. `modifiers`：list / create / load / update / delete
6. `value-domains`：list / create / load / update / delete
7. `object tags`：list / alter
8. `owner`：get

### 8.3.3 数据权限管理接口

当前已落地：

1. `GET /admin/tenant/current/roles/{roleId}/data-privileges`
2. `PUT /admin/tenant/current/roles/{roleId}/data-privileges`
3. `GET /admin/tenant/current/members/{memberId}/data-privileges`
4. `PUT /admin/tenant/current/members/{memberId}/data-privileges`
5. `DELETE /admin/tenant/current/members/{memberId}/data-privileges`

说明：

1. 角色数据权限仍然是主模型。
2. 成员数据权限底层仍然通过“隐藏附加角色”实现，不是 direct user privilege。
3. 这组接口挂在 `tenant` 模块上，是因为它表达的是租户成员/角色管理语义，不是 Gravitino 控制面语义。



这部分是当前方案里必须单独强调的点：

**不能把 Gravitino 返回里的 `audit` 当成租户隔离或用户归属信息。**

### 8.4.1 当前 Gravitino 返回里实际有什么

按当前 Gravitino DTO 设计，绝大多数对象都会带 `audit` 字段，例如：

1. `MetalakeDTO`
2. `CatalogDTO`
3. `SchemaDTO`
4. `TableDTO`
5. `UserDTO`
6. `RoleDTO`
7. `UnitDTO`
8. `MetricDTO`
9. `WordRootDTO`

但 `audit` 的字段固定只有：

1. `creator`
2. `createTime`
3. `lastModifier`
4. `lastModifiedTime`

它表达的是：

1. 谁创建了对象
2. 什么时候创建
3. 谁最后修改
4. 什么时候最后修改

它**不表达**：

1. 当前租户是谁
2. 当前用户是否有权看到这个对象
3. 对象属于哪个用户
4. 对象当前 owner 是谁
5. 当前请求经过权限过滤后的可见范围

所以：

1. `audit.creator != 用户归属`
2. `audit.creator != owner`
3. `audit.creator != 当前可见用户`

### 8.4.2 设计结论

因此，不能采用下面这种错误实现：

1. `studio-service` 先从 Gravitino 拉全量列表
2. 再根据 `audit.creator` 做用户筛选
3. 再把筛选后的结果返回前端

这会直接导致：

1. 安全边界错误
2. 分页总数错误
3. 列表与详情语义不一致
4. owner / role 授权场景被误判

### 8.4.3 正确隔离边界

#### 租户隔离

租户隔离不靠 `audit`，而靠：

1. `studio-service -> Gravitino` 的租户上下文头
2. Gravitino 服务端的租户存储过滤
3. Gravitino 服务端按租户解析后的对象访问范围

也就是说：

1. `tenant_id / tenant_code` 应该来自请求上下文
2. 不应该从 Gravitino DTO 里“猜出来”

#### 用户隔离

用户可见范围不靠 `audit`，而靠：

1. 当前认证用户
2. 当前用户绑定的角色
3. Gravitino RBAC 授权结果

也就是说：

1. 用户是否能看到对象，是授权结果
2. 不是对象 `creator` 字段决定的

### 8.4.4 Studio-Service 的返回职责

`studio-service` 对前端的职责应该是：

1. **基于 Gravitino 已完成语义校验和授权校验的结果做最终返回组装**
2. 补充当前上下文中的平台字段
3. 做字段映射、聚合与错误映射
4. 在需要时，可以追加调用 Gravitino 相关接口组装前端最终所需字段，例如 `owner`

但 `studio-service` **不应该承担主安全过滤器** 的职责。

因此，返回契约应遵守以下规则：

1. 如果 Gravitino 已经返回单对象或列表，`studio-service` 可以做对象级聚合与返回整形，但不能把未授权对象先拉回来再靠本地逻辑做主安全筛选。
2. `owner` 这类不在对象 DTO 内的字段，允许由 `studio-service` 通过额外调用 Gravitino owner 接口进行组装。
3. 列表接口的 `items` 与 `total` 必须对应**过滤后的可见结果**，不能是“先全量 count 再本地删几项”。
4. 对前端真正需要的租户信息，可以由 `studio-service` 从当前上下文补充，例如：
   - `tenantId`
   - `tenantCode`
   - `currentUserId`
5. 这些补充字段属于 **Studio 前端上下文**，不是 Gravitino 原始对象字段。

### 8.4.5 当前阶段推荐返回策略

当前产品阶段建议采用以下策略：

1. **业务对象主数据**：继续以 Gravitino 原始对象字段为核心，例如 `name/code/comment/properties/audit`。
2. **owner 等派生字段**：由 `studio-service` 按需额外调用 Gravitino 接口后做聚合返回，但 owner 语义本身必须来自 Gravitino 原生 owner 关系，而不是本地猜测。
3. **平台上下文字段**：由 `studio-service` 统一补充当前 `tenantId/tenantCode/currentUserId`，但不把它伪装成 Gravitino 原生字段。
4. **权限结果**：只返回当前请求已经有权看到的数据，不额外返回“被过滤掉但其实存在”的对象信息。
5. **owner 信息**：如果前端需要 owner，优先由 `studio-service` 调 Gravitino owner 接口组装返回，不能从 `audit.creator` 推断。

### 8.4.6 当前文档结论

本方案固定为：

1. `audit` 只代表审计信息，不代表租户隔离或用户归属。
2. 租户隔离与用户隔离必须优先在 Gravitino 侧完成。
3. 语义资产残缺逻辑必须优先按 Gravitino 原生语义补齐，而不是在 `studio-service` 里发明替代语义。
4. `studio-service` 负责面向前端的最终返回组装，包括必要时通过额外 Gravitino 调用补齐 `owner` 等字段。
5. `studio-service` 不负责基于 `audit` 做主安全过滤。

---

## 9. 命令语义（禁止继续使用 sync）

以下命名必须替换：

1. `syncRoleDataPrivileges` -> `replaceRolePrivileges`
2. `syncUser` -> `createUser` / `deleteUser`
3. `syncRoleMembership` -> `replaceUserRoles`

Datapillar 不是一个离线同步器，而是 Gravitino 的前端控制面。

所以写操作都必须按命令式 CRUD 理解：

1. 创建用户
2. 删除用户
3. 创建角色
4. 删除角色
5. 替换角色权限
6. 替换用户角色绑定
7. 替换用户附加数据权限

---

## 10. 关键业务流程

## 10.1 创建用户

```text
studio create user
  -> insert local users / tenant_users / user_roles
  -> GravitinoUserService.createUser(username, userId, operator)
  -> GravitinoUserRoleService.replaceUserRoles(username, roleCodes, operator)
```

## 10.2 更新用户角色

```text
studio replace local user_roles
  -> GravitinoUserRoleService.replaceUserRoles(username, roleCodes, operator)
```

## 10.3 删除用户 / 移除成员

```text
revoke all user roles in Gravitino
  -> clear user override role binding
  -> delete Gravitino user (if product requires hard delete)
  -> delete local membership / user data
```

## 10.4 更新角色数据权限

```text
studio role privilege update
  -> GravitinoRolePrivilegeService.replaceRoleDataPrivileges(roleName, domain, commands, operator)
```

## 10.5 更新用户附加数据权限

```text
studio user extra privilege update
  -> ensure hidden role user_override_{userId}
  -> replace hidden role privileges
  -> ensure user bound to hidden role
```

---

## 11. 离职与禁用场景

## 11.1 员工离职

正确流程：

1. 本地禁用用户 / 终止成员资格
2. `replaceUserRoles(userId, username, [])`
3. 移除用户附加角色绑定
4. 必要时删除 Gravitino 用户
5. 如果用户仍是 metadata owner，先执行 owner 转移

## 11.2 岗位变化

正确流程：

1. 更新基础角色绑定
2. 保留或调整用户附加角色
3. 不直接改 role privilege 的公共定义

---

## 12. 一致性与事务边界

这是分布式双写，不要假装单库事务。

约束如下：

1. Studio 本地是用户、角色、成员关系的主数据源。
2. Gravitino 是数据权限的主执行面。
3. 所有命令走同步写。
4. `integration/gravitino/service/*` 与对应业务 service 必须共同保证补偿边界清晰，禁止把补偿逻辑散在 controller 中。

例如：

1. 本地创建用户成功，远端建用户失败 -> 本地事务回滚。
2. 本地替换成员关系成功，远端 `replaceUserRoles` 失败 -> 本地事务回滚。
3. 若远端已成功、本地提交失败 -> 对应业务 service 与 `integration/gravitino/service/*` 立即做反向补偿。

---

## 13. 实施优先级

## 第一阶段：优先补齐 Gravitino 原生能力

1. **P0：先将 `catalog-dataset` 语义资产链路收敛到原生 `Event -> Normalize -> Hook -> Operation` 规范，把已存在的 owner / authorization 副作用从 `DatasetOperationDispatcher` 拆出去。**
2. **P0：在 Gravitino 侧扩展统一对象类型，至少补齐 `METRIC / WORDROOT / UNIT / MODIFIER`。**
3. **P0：在 Gravitino 侧打通语义资产进入统一 role privilege / owner / object operation 模型。**
4. **P0：在 Gravitino 侧修正 `external_user_id` 的语义与落库行为，禁止退化逻辑。**
5. **P0：在 Gravitino 侧核对并打通 `user -> role binding` 相关 API/行为。**

## 第二阶段：先补齐 Gravitino `java-client`，再收口 studio-service

1. 优先补齐 `datapillar-gravitino/clients/client-java` 的 semantic typed client / object wrapper。
2. 至少覆盖 `metric / metric version / modifier / wordroot / unit / value-domain` 这一批当前缺失或半残的对象级能力。
3. 明确 `studio-service` 必须使用官方 `java-client`，不得继续自研 transport façade。
4. 收口所有 Gravitino 调用。
5. 所有 Gravitino 前端接口统一收敛到业务模块路径，禁止再新增 `/admin/gravitino/**` 与 `/biz/gravitino/**` 这类控制面前缀。
6. 移除 `sync` 命名，统一改为 CRUD / replace。
7. 固化 `external_user_id = users.id`。
8. 旧的 `/biz/governance/**` 进入退场路径；`/admin/tenant/current/roles/**/data-privileges` 与 `/admin/tenant/current/members/**/data-privileges` 作为当前正式管理入口继续保留。

## 第三阶段：补齐用户附加数据权限

1. 引入隐藏附加角色模型。
2. 新增用户附加数据权限接口。
3. 前端展示“基础角色权限 + 个人附加权限”。

---

## 14. 可执行 TODO 清单

> 以下状态按 **当前代码真实落地** 更新；不再保留已经被否决的旧方案勾选项。

### 14.1 Studio 目录与分层

- [x] Gravitino 资源粒度 service 已统一收敛到 `integration/gravitino/service/*`。
- [x] `module/gravitino/controller/*` 已删除，不再作为对前端暴露入口。
- [x] `GravitinoAccessService / GravitinoMetadataService / GravitinoSemanticService` 这类单体 service 已删除。
- [x] `SetupServiceImpl`、`TenantServiceImpl`、`UserServiceImpl`、`RoleServiceImpl` 已改为依赖资源粒度的 Gravitino integration service。
- [x] `MetadataBizController`、`SemanticBizController` 已落地为业务资源前端入口。
- [x] `TenantRoleAdminController`、`TenantMemberAdminController` 已落地数据权限管理接口。

### 14.2 外部接口形态

- [x] 前端接口不再暴露 `metalake` 概念。
- [x] 业务资源接口已收敛到 `/biz/metadata/**` 与 `/biz/semantic/**`。
- [x] 数据权限管理接口已收敛到 `tenant` 业务模块，而不是 `/admin/gravitino/**`。
- [x] setup 阶段远端初始化已统一由 `GravitinoSetupServiceImpl` 负责。

### 14.3 Gravitino 侧能力

- [x] `metric / wordroot / unit / modifier / value-domain` 已进入统一 metadata object / owner / authorization 模型。
- [x] `modifier` 底层 entity / table 命名已统一为 `MODIFIER / modifier_meta`。
- [x] dataset dispatcher 已收敛为 `Event -> Normalize -> Hook -> Operation`。
- [x] dataset hook 已补齐 create 前校验、create 后 owner 绑定、owner 失败回滚、delete 后 privilege 清理。
- [x] `replaceRolesForUser` 原生链路已补齐，可供 Studio 直接做全量替换。
- [x] `X-External-User-Id` 语义已在 Gravitino 侧固定，不再混用 `X-User-Id`。

### 14.4 Java Client 与 Studio 调用

- [x] semantic 相关 typed client wrapper 已补齐到 `datapillar-gravitino/clients/client-java`。
- [x] Studio 侧继续使用官方 `java-client`，不再新增 transport façade。
- [x] setup / tenant 初始化场景已拆出 `createCatalogIfAbsent`、`createSchemaIfAbsent` 这类引导语义方法，避免把 bootstrap 返回值和业务 CRUD 返回值搅成一坨。

### 14.5 当前测试状态

- [x] `GravitinoSetupServiceImplTest` 已对齐新的资源粒度 service。
- [x] `UserServiceImplTest` 已覆盖 `createUser / deleteUser / user data privileges` 关键路径。
- [x] `RoleServiceImplTest` 已覆盖 `createRole / deleteRole / removeRoleMembers / role data privileges` 关键路径。
- [x] `TenantServiceImplTest` 已覆盖 tenant 初始化、回滚、existing key 复用路径。
- [x] `MetadataBizControllerTest`、`SemanticBizControllerTest` 已新增并通过。

### 14.6 当前剩余 TODO

- [x] `MetadataBizController`、`SemanticBizController` 的 controller-facing request / response DTO 已拆回 `dto/metadata/*`、`dto/semantic/*`，`dto/gravitino/*` 已删除。
- [x] `MetadataBizController`、`SemanticBizController` 已不再使用 `JsonNode` 作为 Controller 契约。
- [x] `integration/gravitino/service/*` 已改为 typed command / internal model，不再对上暴露 controller DTO 与 `JsonNode`。

## 15. 最终裁决


本方案不接受以下做法：

1. 继续只做 `role -> privilege` 半打通。
2. 继续让 Gravitino client 调用散落在多个业务 service。
3. 继续把写操作命名成 `sync`。
4. 直接引入“用户直授权限”第二套平行模型。

最终方案固定为：

**Studio RBAC ↔ Gravitino RBAC 全量打通，用户单独数据权限通过用户附加角色实现。**
