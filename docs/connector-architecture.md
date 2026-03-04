# Datapillar Connector 架构设计（修订版）

## 1. 背景与定位

Datapillar 引入 Connector 的目的只有一个：**屏蔽底层开源/二开系统差异**，把业务语义与下游协议彻底解耦。

这不是网关直通方案，也不是前端编排方案。

本次改造必须在上线前一次性硬切完成：旧路径直接删除，不保留灰度兼容与回滚分支。

标准调用链必须是：

```text
Frontend
  -> API Gateway（只做鉴权/上下文注入/路由到 Studio）
    -> Studio Domain Service（上层接口）
      -> Connector Kernel（统一控制面）
        -> Connector Plugin（Gravitino / Airflow / 第三方）
          -> External Engine
```

---

## 2. 本次修订要解决的问题

基于当前仓库实现，旧版设计存在以下硬伤：

1. `connectorId` 用途自相矛盾：一边说不做请求级动态路由，一边又有 `RouteResolver`。
2. 入口不收口：业务层可直接调 `AirflowClient`，导致 Kernel 的统一能力被绕过。
3. 超时/重试职责重叠：Kernel 与 Connector 同时声明负责，运行时语义冲突。
4. 幂等只有字段没有落地：未对齐下游接口天然幂等语义与稳定幂等键约束。
5. 上下文字段不完整：缺失 `actor/impersonation` 等审计关键字段。
6. 错误模型与 `datapillar-common` 的 `ErrorType` 常量不一致。

---

## 3. 目标与非目标

### 3.1 目标

1. 定义稳定 SPI，新增 Connector 不改 Kernel 核心代码。
2. 固化单入口：Studio 只能通过 Domain Service（上层接口）调 Kernel，禁止直连下游 client。
3. 建立 Flink 风格的插件发现/校验/冲突报错机制。
4. 统一上下文注入、幂等、可观测、错误归一。
5. 与现有工程约束对齐：`HeaderConstants`、`ErrorType`、统一错误语义。

### 3.2 非目标

1. 不引入分布式事务框架。
2. 不把 Connector 拆成独立微服务。
3. 必须硬切，不做双轨兼容与灰度分流（旧逻辑与新逻辑并行运行）。

---

## 4. 核心设计决策（强约束）

### 4.1 单入口约束

1. 业务代码禁止直接依赖 `AirflowClient` / `GravitinoClient`。
2. 业务代码只依赖现有领域 Service（如 `SetupService`、`WorkflowService`、`RoleService`、`SqlService`）。
3. 领域 Service 内部统一调用 `ConnectorKernel`，不再直连下游 client。
4. 不新增 `*ConnectorService` 命名层，沿用项目现有 `*Service/*BizService` 约定。
5. 前端只允许调用 Gateway 暴露的 Studio/AI 领域 API，禁止直连 `onemeta/gravitino/airflow plugin` 路径。
6. 前端请求禁止携带 `connectorId/metalake/dag_id` 作为路由控制参数。
7. Gateway 只允许暴露领域 API（`/api/studio/**`、`/api/ai/**`），禁止保留 `/api/onemeta/**`、`/datapillar/**`、`/plugins/datapillar/**` 引擎直通路由。
8. Gateway 不得依赖客户端传入的上下文头做业务路由，租户/用户上下文只能由鉴权链路注入。

### 4.2 路由约束（固定绑定，不做请求级动态路由）

1. `connectorId` 只在服务内部使用，不来自前端请求参数。
2. 领域到 connector 的绑定是固定的、可审计的：
3. Setup/Metadata 领域 -> `gravitino`
4. Workflow 领域 -> `airflow`
5. 第三方 connector 切换发生在 ConnectorRuntime 装配层，不发生在请求 payload。

### 4.3 控制面职责收口

以下能力只允许 Kernel 实现：

1. 超时策略
2. 重试策略
3. 幂等状态流转
4. 指标与审计日志
5. 异常归一

Connector 插件只负责：

1. 协议适配（HTTP/SDK）
2. 参数与 header 注入
3. 下游异常翻译为平台异常

### 4.4 Gravitino 传输策略（只允许 Java Client）

1. Gravitino Connector 必须只使用官方 Java Client（`datapillar-gravitino/clients/client-java`）。
2. SDK 缺失能力时，必须在 `datapillar-gravitino` 二开补齐 Client 接口与实现，再由 Connector 调用。
3. `datapillar-connector-gravitino` 内禁止新增 HTTP 直连适配器作为兜底路径。
4. 协议细节只留在 Client 层，Connector 层只处理领域语义与错误映射。
5. `datapillar-gravitino` 是独立 Gradle 工程，不在根 Maven Reactor（`Datapillar/pom.xml`）中。
6. 本地调试前必须执行：`cd datapillar-gravitino && ./gradlew :clients:client-java:publishToMavenLocal -x test`。
7. 若 connector 同时依赖 runtime 包，额外执行：`./gradlew :clients:client-java-runtime:publishToMavenLocal -x test`。
8. 未完成本地发布前，禁止启动 Gravitino Connector 联调流程。

### 4.5 租户隔离与双 metalake 模型（强制）

1. 每个租户在“用户打通”完成后，必须存在两类 metalake：`oneMeta`、`oneSemantics`。
2. 这两个名字是租户内的固定逻辑名，不允许前端传 metalake 决定路由。
3. setup 阶段必须通过 Gravitino Connector 调用 Java Client 执行：
4. `syncUser -> createMetalake(oneMeta) -> createMetalake(oneSemantics) -> syncRoleDataPrivileges`。
5. `createMetalake` 必须幂等：已存在按成功处理，不得因重复创建中断 setup。
6. 运行时固定绑定：
7. Metadata/Security 领域 -> `oneMeta`
8. Semantic 领域 -> `oneSemantics`
9. Connector 必须从可信租户上下文构造请求，不接受请求体携带 metalake 参数。

### 4.6 Airflow 传输策略（只允许 HTTP）

1. Airflow Connector 只允许通过 `datapillar-airflow-plugin` 暴露的 HTTP API 对接。
2. 当前不引入 Airflow Java SDK 路径，也不引入本地进程内嵌调用路径。
3. 插件路径按 Airflow 版本配置：
4. Airflow 3.x 使用 `/plugins/datapillar`
5. Airflow 2.x 使用 `/datapillar`
6. `pluginPath` 由配置决定，业务代码不得自行拼接或硬编码路径。
7. Studio 现有 `AirflowClient` 的 HTTP 逻辑迁入 `datapillar-connector-airflow` 后必须保持同等语义（鉴权、超时、错误映射）。
8. `POST /dags` 请求体契约强制统一为 `workflow` 模型（`workflow.workflow_name/...`），禁止继续使用 `namespace + dag` 旧结构。
9. Airflow 2.x/3.x 插件实现必须对齐同一请求契约，不允许按版本分叉两套 payload。

### 4.7 Airflow 租户隔离（强制）

1. 必须做租户隔离；Airflow 的 DAG 元数据与 `dags_folder` 是共享资源，不隔离就有串租户风险。
2. DAG ID 必须由后端按固定规则生成：`dp_{tenantCode}_w{workflowId}`。
3. `tenantCode` 是租户名称语义的系统唯一编码；不使用可变展示名拼接 `dag_id`。
4. `tenantCode` 必须先规范化为小写，且满足：`^[a-z0-9][a-z0-9_-]{1,63}$`；不满足直接拒绝创建租户。
5. `tenantCode` 是不可变主标识，创建后禁止修改；`dag_id`、文件目录、幂等键都基于该不变性。
6. 前端请求体禁止传 `dag_id`；业务层也禁止手工拼接 `dag_id`。
7. Connector 调用插件时必须注入 `X-Tenant-Code`（可同时注入 `X-Tenant-Id` 供审计）。
8. 插件侧对所有 `/{dag_id}` 读写接口必须校验租户一致性；`X-Tenant-Code` 与 `dag_id` 不匹配直接拒绝（`FORBIDDEN`）。
9. 插件 `GET /dags` 必须默认按 `dp_{tenantCode}_*` 过滤，不得返回其他租户 DAG。
10. DAG 文件必须按租户目录优先落盘：`{dags_folder}/datapillar/{tenantCode}/wf_{workflowId}.py`。
11. 不做旧命名兼容：`datapillar_project_*` 一律废弃，不保留 fallback 分支。

---

## 5. 模块结构（落地形态）

```text
Datapillar/
  datapillar-connector/
    pom.xml
    datapillar-connector-spi/
      pom.xml
      src/main/java/com/sunny/datapillar/connector/spi/
        ConnectorFactory.java
        ConnectorFactoryContext.java
        Connector.java
        ConnectorManifest.java
        OperationSpec.java
        ConnectorInvocation.java
        ConnectorContext.java
        IdempotencyDescriptor.java
        TimeoutDescriptor.java
        ConnectorResponse.java
        ConnectorException.java
        ErrorType.java
      src/test/java/com/sunny/datapillar/connector/spi/
        ConnectorManifestTest.java
        ConnectorInvocationTest.java

    datapillar-connector-runtime/
      pom.xml
      src/main/java/com/sunny/datapillar/connector/runtime/
        ConnectorKernel.java
        DefaultConnectorKernel.java
      src/main/java/com/sunny/datapillar/connector/runtime/bootstrap/
        ConnectorFactoryLoader.java
        ConnectorRegistry.java
        ConnectorFactoryHelper.java
      src/main/java/com/sunny/datapillar/connector/runtime/config/
        ConnectorRuntimeProperties.java
        ConnectorInstanceProperties.java
      src/main/java/com/sunny/datapillar/connector/runtime/context/
        ConnectorContextResolver.java
      src/main/java/com/sunny/datapillar/connector/runtime/idempotency/
        ConnectorIdempotencyStore.java
        IdempotencyGuard.java
      src/main/java/com/sunny/datapillar/connector/runtime/execute/
        RetryExecutor.java
        TimeoutExecutor.java
      src/main/java/com/sunny/datapillar/connector/runtime/error/
        RuntimeErrorMapper.java
      src/main/java/com/sunny/datapillar/connector/runtime/observe/
        ConnectorMetricsRecorder.java
        ConnectorAuditLogger.java
      src/main/java/com/sunny/datapillar/connector/runtime/spring/
        ConnectorRuntimeAutoConfiguration.java
      src/test/java/com/sunny/datapillar/connector/runtime/
        DefaultConnectorKernelTest.java
        ConnectorFactoryHelperTest.java
        ConnectorRegistryTest.java
        IdempotencyGuardTest.java
        RetryExecutorTest.java
```

说明：

1. `datapillar-connector` 是库模块，不是独立服务。
2. `spi/runtime` 必须保持引擎无关。
3. `gravitino/airflow` 为内置插件，第三方按同样 SPI 扩展。
4. 上述 `datapillar-connector-spi` 与 `datapillar-connector-runtime` 目录和文件是强制清单，不得删减核心文件。

### 5.1 必要代码文件（必须存在）

1. `ConnectorFactory.java`：定义 `connectorIdentifier/requiredOptions/optionalOptions/create`。
2. `Connector.java`：统一入口 `manifest/invoke/initialize/destroy`。
3. `ConnectorInvocation.java`：一次调用的完整上下文（connectorId、operationId、payload、idempotency、timeout）。
4. `DefaultConnectorKernel.java`：运行时主编排（预校验 -> 幂等 -> 重试/超时 -> 错误映射 -> 指标审计）。
5. `ConnectorFactoryHelper.java`：启动期强校验（重复标识、缺配置、未消费配置）。
6. `ConnectorRegistry.java`：维护 `connectorId -> Connector` 映射。
7. `ConnectorIdempotencyStore.java` + `IdempotencyGuard.java`：统一幂等执行入口（当前默认 `Noop`，依赖下游接口幂等语义，不引入额外业务表）。
8. `ConnectorContextResolver.java`：只从可信租户上下文组装 `ConnectorContext`。
9. `RuntimeErrorMapper.java`：统一异常到平台 `ErrorType`。
10. `ConnectorRuntimeAutoConfiguration.java`：Spring 装配 runtime 内核。

### 5.2 关键接口骨架（必须有）

```java
public interface ConnectorFactory {
    String connectorIdentifier();
    Set<ConfigOption<?>> requiredOptions();
    Set<ConfigOption<?>> optionalOptions();
    Connector create(ConnectorFactoryContext context);
}
```

```java
public interface Connector {
    ConnectorManifest manifest();
    ConnectorResponse invoke(ConnectorInvocation invocation);
    default void initialize() {}
    default void destroy() {}
}
```

```java
public interface ConnectorKernel {
    ConnectorResponse invoke(ConnectorInvocation invocation);
}
```

```java
public interface ConnectorIdempotencyStore {
    OperationState startOrResume(String key, String step);
    void markSucceeded(String key, String step);
    void markFailed(String key, String step, String errorType, String errorMessage);
}
```

### 5.3 插件发现强约束

1. 每个 connector 插件模块必须提供：
2. `META-INF/services/com.sunny.datapillar.connector.spi.ConnectorFactory`
3. 未提供该文件则视为插件不可发现，启动直接失败。

---

## 6. SPI 设计（对齐 Flink Factory 模式）

### 6.1 工厂接口（Flink `Factory` 映射）

```java
public interface ConnectorFactory {
    String connectorIdentifier();                 // 类似 factoryIdentifier()
    Set<ConfigOption<?>> requiredOptions();       // 启动校验必填项
    Set<ConfigOption<?>> optionalOptions();       // 启动校验可选项
    Connector create(ConnectorFactoryContext context);
}
```

约束：

1. `connectorIdentifier` 全局唯一，重复即启动失败。
2. 工厂必须在 `create` 前完成 options 校验。
3. 禁止把下游业务路由信息塞进 factory options。

### 6.2 Connector 接口

```java
public interface Connector {
    ConnectorManifest manifest();
    ConnectorResponse invoke(ConnectorInvocation invocation);
    default void initialize() {}
    default void destroy() {}
}
```

### 6.3 Manifest（能力声明）

```java
public record ConnectorManifest(
    String connectorId,
    String version,
    Map<String, OperationSpec> operations
) {}
```

规则：

1. `operationId` 是插件内部能力标识，不是前端 API。
2. operation 命名必须分层：`{engine}.{domain}.{resource}.{action}`。
3. 禁止在 Kernel 中写任何 `if (gravitino) / if (airflow)` 业务分支。

### 6.4 调用模型（内部，不对外暴露）

```java
public record ConnectorInvocation(
    String connectorId,
    String operationId,
    JsonNode payload,
    ConnectorContext context,
    IdempotencyDescriptor idempotency,
    TimeoutDescriptor timeout
) {}
```

关键点：

1. `connectorId` 必须存在，但只能由 Studio Domain Service 固定填充。
2. 前端/网关层不允许传入 `connectorId`。

---

## 7. Runtime（Kernel）流程

```text
DomainService.execute(...)
  -> Kernel.preValidate(connectorId, operationId)
  -> IdempotencyGuard.startOrResume(...)
  -> RetryExecutor(timeout, retryPolicy).invoke(connector)
  -> ErrorMapper.map(...)
  -> Metrics/Log/Audit
  -> IdempotencyGuard.markSucceeded/markFailed
```

Kernel 必须提供：

1. `discoverFactories()`：基于 `ServiceLoader` 加载所有 `ConnectorFactory`。
2. `validateUniqueIdentifier()`：重复标识符启动失败，并输出冲突实现类列表。
3. `validateRequiredOptions()`：缺配置启动失败。
4. `validateUnsupportedOptions()`：拒绝未消费配置项（Flink `FactoryUtil` 风格）。

---

## 8. ConnectorContext 与 Header 注入标准

`ConnectorContext` 必须覆盖现有安全链路字段：

```java
public record ConnectorContext(
    Long tenantId,
    String tenantCode,
    Long userId,
    String username,
    String principalSub,
    Long actorUserId,
    Long actorTenantId,
    boolean impersonation,
    String traceId,
    String requestId
) {}
```

注入规则：

1. 来自可信上下文（`TenantContext` + 安全过滤链），不是请求体透传。
2. 下游 header 统一使用平台常量语义：
3. `X-Tenant-Id`, `X-Tenant-Code`, `X-User-Id`, `X-Username`
4. `X-Principal-Sub`, `X-Actor-User-Id`, `X-Actor-Tenant-Id`, `X-Impersonation`
5. `X-Trace-Id`, `X-Request-Id`

---

## 9. 错误模型（对齐 datapillar-common）

Connector 异常必须归一到现有 `ErrorType`：

1. `BAD_REQUEST`
2. `UNAUTHORIZED`
3. `FORBIDDEN`
4. `NOT_FOUND`
5. `CONFLICT` / `ALREADY_EXISTS`
6. `BAD_GATEWAY`（下游连接或协议错误）
7. `SERVICE_UNAVAILABLE`（下游超时/不可用）
8. `INTERNAL_ERROR`

禁止自造另一套并行错误枚举。

---

## 10. 幂等机制（依赖下游语义）

写操作幂等以“稳定幂等键 + 下游接口幂等语义”为主，不引入新的 connector 幂等业务表。

规则：

1. Domain Service 生成稳定幂等键（只用不可变业务主键）。
2. Kernel 保留 `IdempotencyGuard` 执行入口；当前默认 `NoopConnectorIdempotencyStore`，不做持久化落表。
3. 下游接口必须满足幂等语义：重复请求返回已存在/无副作用成功（例如 `createMetalake` 已存在按成功）。
4. 同幂等键并发冲突由下游能力与业务主键唯一性兜底，不在 Studio/Connector 侧新增补丁式存储。
5. 使用 `tenantCode` 参与幂等键的前提是 `tenantCode` 不可变。

键示例（修订）：

1. Setup 用户打通：`setup:{tenantId}:{adminUserId}`
2. Gravitino 资源创建：`gravitino:{tenantId}:{resourcePath}`
3. Airflow DAG 发布：`airflow:{tenantCode}:{workflowId}:{version}`

---

## 11. 配置模型

只允许 connector 实例连接参数，不允许业务路由配置。

```yaml
connector:
  instances:
    gravitino:
      endpoint: http://datapillar-gravitino:8090
      metalakes:
        metadata: oneMeta
        semantic: oneSemantics
      connectTimeoutMs: 2000
      readTimeoutMs: 5000
      retry:
        maxAttempts: 2
        backoffMs: 200
    airflow:
      endpoint: http://airflow:8080
      pluginPath: /plugins/datapillar # Airflow 3.x；Airflow 2.x 配置为 /datapillar
      connectTimeoutMs: 2000
      readTimeoutMs: 8000
      retry:
        maxAttempts: 2
        backoffMs: 200
```

约束：

1. 重试主策略在 Kernel，connector 只可声明默认建议值。
2. 禁止配置 `operation -> connector` 动态映射。
3. Gravitino Connector 不支持协议切换配置，只保留 Java Client 路径。
4. Gravitino 的 metalake 只允许配置“领域固定映射”，不允许请求级覆盖。
5. Airflow Connector 只支持 HTTP 协议，且只能调用 datapillar-airflow-plugin 约定的 API。

---

## 12. Gravitino Connector 当前实现范围（V1）

不是“只做权限同步”，`Catalog/Schema/Table` 必须是 V1 必选能力。

### 12.1 Metadata 领域（V1 必做）

1. Metalake：`listMetalakes`、`loadMetalake`、`createMetalake`（供 setup 初始化 `oneMeta/oneSemantics`）
2. Metalake 能力统一由 `MetalakeService` 对外提供，禁止散落在其他 service 中临时拼装
3. Catalog：`testCatalogConnection`、`listCatalogs`、`createCatalog`、`updateCatalog`、`deleteCatalog`
4. Schema：`listSchemas`、`getSchema`、`createSchema`、`updateSchema`、`deleteSchema`
5. Table：`listTables`、`getTable`、`createTable`、`updateTable`、`deleteTable`
6. Tag：`listTags`、`createTag`、`getObjectTags`、`associateObjectTags`
7. 实现要求：全部通过 Java Client 调用；缺能力先在 `client-java` 二开补齐

### 12.2 Security 领域（V1 必做）

1. 用户同步：`syncUser`
2. 角色数据权限：`listRoleDataPrivileges`、`syncRoleDataPrivileges`
3. 实现要求：全部通过 Java Client 调用；缺能力先在 `client-java` 二开补齐

### 12.3 Semantic 领域（V1.5）

1. WordRoot：`list/get/create/update/delete`
2. Metric：`list/get/register/update/delete`
3. MetricVersion：`listVersions/getVersion/updateVersion/switchVersion`
4. Unit：`list/get/create/update/delete`
5. Modifier：`list/get/create/update/delete`
6. ValueDomain：`list/getByCode/create/update/delete`
7. 若 Java Client 暂未覆盖语义接口，必须先在 `client-java` 二开补齐，再接入 Connector

### 12.4 Airflow Connector（V1）

1. DAG 发布、更新、暂停、恢复、删除
2. DAG Run 触发与查询
3. Task 状态、日志、重跑
4. token/session 生命周期由 connector 内部托管
5. 实现要求：仅 HTTP，对接 datapillar-airflow-plugin（Airflow 2.x `/datapillar`，Airflow 3.x `/plugins/datapillar`）
6. `dag_id` 规则固定为 `dp_{tenantCode}_w{workflowId}`，由后端生成，不接受前端传入
7. DAG 文件路径固定为 `{dags_folder}/datapillar/{tenantCode}/wf_{workflowId}.py`
8. 插件必须强校验 `X-Tenant-Code` 与 `dag_id` 一致性，不允许跨租户访问
9. 旧命名 `datapillar_project_*` 不兼容，不保留过渡分支
10. Airflow 2.x/3.x `POST /dags` 入参统一为 `workflow` 模型，不允许继续使用 `namespace + dag` 旧模型

---

## 13. 命名与代码结构规范（禁止补丁式命名）

### 13.1 命名规范

1. 禁止抽象词命名：`Provision*`、`Ensure*`、`Bootstrap*Operation*`
2. 统一动词直述：`create*`、`update*`、`delete*`、`list*`、`get*`、`sync*`
3. 幂等“创建或更新”语义统一用 `sync*`，不用 `provision*`
4. DTO 命名：`CreateCatalogRequest`、`UpdateTableRequest` 这类直接业务名
5. 代码注释必须使用英文（Code comments must be written in English）

### 13.2 代码结构（新项目式，按领域切分）

```text
datapillar-connector-gravitino/
  src/main/java/com/sunny/datapillar/connector/gravitino/
    GravitinoConnectorFactory.java
    GravitinoConnector.java
    config/
      GravitinoConnectorConfig.java
    domain/
      metadata/
        MetalakeService.java
        CatalogService.java
        SchemaService.java
        TableService.java
        TagService.java
      security/
        UserService.java
        RolePrivilegeService.java
      semantic/
        WordRootService.java
        MetricService.java
        UnitService.java
        ModifierService.java
        ValueDomainService.java
    transport/
      sdk/
        GravitinoSdkClientFactory.java
        GravitinoMetadataClient.java
        GravitinoSecurityClient.java
        GravitinoSemanticClient.java
    mapper/
      request/
      response/
    error/
      GravitinoErrorMapper.java
```

约束：

1. 禁止一接口一 `*Operation.java` 的碎片化类爆炸。
2. 统一通过领域 Service 暴露能力，内部再拆请求构造与响应映射。
3. Studio 业务 Service 只依赖领域 Service 接口，不依赖 Java Client 细节。
4. Connector 内禁止出现 HTTP 兜底分支与协议路由分支。
5. setup 中双 metalake 初始化必须走 `MetalakeService`，禁止散落在 setup 代码里直接调底层 client。

---

## 14. 发现与打包策略

当前只支持 **classpath 插件发现**：

1. 所有 connector jar 随发行包进入 `libs/*`。
2. JVM classpath 启动时由 `ServiceLoader` 发现。
3. 不做运行时热加载；新增插件需重启服务生效。

说明：当前 `datapillar-distribution` 已是 `libs/*` 启动模型，文档必须与此一致。

---

## 15. 硬切实施计划（上线前一次性）

### 15.1 前置依赖发布（必须先做）

1. 在 `datapillar-gravitino` 执行：`./gradlew :clients:client-java:publishToMavenLocal -x test`。
2. 若 connector 依赖 runtime 包，再执行：`./gradlew :clients:client-java-runtime:publishToMavenLocal -x test`。
3. 验证本地仓库已存在 `org/apache/gravitino/gravitino-client-java` 对应版本目录后，再启动 Studio/Connector 调试。

### 15.2 一次性硬切改造

1. 增加 `datapillar-connector` 聚合模块与 `spi/runtime`，并启用 `ServiceLoader` 发现、冲突检查、options 校验。
2. Gravitino 能力一次性收口到 `datapillar-connector-gravitino`（Metadata + Security），setup 固定为 `syncUser -> 双 metalake 初始化 -> syncRoleDataPrivileges`。
3. Workflow 领域一次性改为 `ConnectorKernel -> datapillar-connector-airflow`，移除业务层对 `AirflowClient` 的直接依赖。
4. `buildDagId` 一次性切换为 `dp_{tenantCode}_w{workflowId}`，并统一出站注入 `X-Tenant-Code`。
5. `datapillar-airflow-plugin` 一次性切换为租户目录写入与租户强校验，并统一 `POST /dags` 请求契约。
6. 一次性删除 RPC 一致性路径及其在 Studio/Connector 中的所有引用，幂等仅依赖下游接口语义与稳定业务主键。

### 15.3 删除无用逻辑（必须完成）

1. 删除 `datapillar_project_*` 的 DAG 命名生成与解析逻辑。
2. 删除 Airflow 插件中 `namespace + dag` 的旧请求模型解析。
3. 删除任何缺失 `X-Tenant-Code` 仍继续执行的兜底路径。
4. 删除 DAG 根目录平铺写入逻辑，只保留 `{dags_folder}/datapillar/{tenantCode}`。
5. 清理历史旧命名 DAG 文件与对应无效调度元数据，避免 scheduler 继续加载旧 DAG。
6. 删除 Studio 业务层所有下游直连调用，仅保留统一 Domain Service + ConnectorKernel 入口。
7. 删除 RPC 一致性相关 DAO/Repository/配置项与初始化脚本，不保留兼容读取分支。

### 15.4 全量设计 TODO（覆盖本文件全部约束，可打钩）

#### A. 依赖与基线

- [x] 执行 `cd datapillar-gravitino && ./gradlew :clients:client-java:publishToMavenLocal -x test`
- [x] 如依赖 runtime，再执行 `./gradlew :clients:client-java-runtime:publishToMavenLocal -x test`
- [x] 校验本地仓库存在 `~/.m2/repository/org/apache/gravitino/gravitino-client-java/<version>/`
- [x] 校验本地仓库存在 `~/.m2/repository/org/apache/gravitino/gravitino-client-java-runtime/<version>/`（若使用）

#### B. Connector 基础模块（SPI/Runtime）

- [x] 新增 `datapillar-connector` 聚合模块与 `datapillar-connector-spi`
- [x] 新增 `datapillar-connector-runtime` 并完成 Spring 自动装配
- [x] 落地 `ConnectorFactory/Connector/ConnectorKernel/ConnectorInvocation` 等 SPI 核心接口
- [x] 落地 `ConnectorFactoryHelper` 启动期校验（重复标识、缺配置、未消费配置）
- [x] 落地 `ServiceLoader` 插件发现与 `ConnectorRegistry` 注册机制
- [x] 落地 `RetryExecutor/TimeoutExecutor`，并确保策略仅在 Kernel 层生效
- [x] 落地 `ConnectorIdempotencyStore + IdempotencyGuard` 统一入口（默认 `Noop`，不新增幂等业务表）
- [x] 删除 RPC 一致性路径及其访问层，connector 幂等不依赖任何额外业务表
- [x] 落地 `RuntimeErrorMapper`，错误归一仅使用 `datapillar-common ErrorType`
- [x] 所有插件模块补齐 `META-INF/services/com.sunny.datapillar.connector.spi.ConnectorFactory`

#### C. 上下文与安全注入

- [x] 落地 `ConnectorContextResolver`，只从可信上下文组装租户/用户/审计字段
- [x] 下游调用统一注入 `X-Tenant-Id/X-Tenant-Code/X-User-Id/X-Username`
- [x] 下游调用统一注入 `X-Principal-Sub/X-Actor-User-Id/X-Actor-Tenant-Id/X-Impersonation`
- [x] 下游调用统一注入 `X-Trace-Id/X-Request-Id`
- [x] 禁止请求体透传租户上下文字段参与路由

#### D. Gravitino Connector（Java Client Only）

- [x] 建立 `datapillar-connector-gravitino` 的 `domain/transport/mapper/error` 结构
- [x] 实现 `MetalakeService` 并在 setup 固定流程接入双 metalake 初始化
- [x] 实现 Metadata V1（Catalog/Schema/Table/Tag）能力
- [x] 实现 Security V1（`syncUser/listRoleDataPrivileges/syncRoleDataPrivileges`）能力
- [x] 语义能力缺失先在 `client-java` 二开补齐，再接入 connector
- [x] 删除 Gravitino connector 中任何 HTTP 兜底与协议路由分支

#### E. Airflow Connector + Plugin（硬切）

- [x] `buildDagId` 统一为 `dp_{tenantCode}_w{workflowId}`
- [x] `tenantCode` 规范化与校验规则落地：`^[a-z0-9][a-z0-9_-]{1,63}$`
- [x] 落地 `tenantCode` 不可变约束（防止 `dag_id`/目录/幂等键漂移）
- [x] Airflow 出站调用全量注入 `X-Tenant-Code`
- [x] Airflow 2.x/3.x `POST /dags` 统一为 `workflow` 模型
- [x] 删除 Airflow 插件 `namespace + dag` 旧请求模型解析逻辑
- [x] 所有 `/{dag_id}` 路由强校验 `X-Tenant-Code` 与 `dag_id` 一致性
- [x] `GET /dags` 默认按 `dp_{tenantCode}_*` 过滤
- [x] DAG 文件写入路径固定为 `{dags_folder}/datapillar/{tenantCode}/wf_{workflowId}.py`
- [x] 删除 DAG 根目录平铺写入逻辑
- [x] 删除 `datapillar_project_*` 命名生成与解析逻辑
- [x] 清理历史旧命名 DAG 文件与无效调度元数据

#### F. Studio 业务接入收口

- [x] Workflow 领域通过 `ConnectorKernel` 调用 Airflow connector
- [x] Setup/Metadata/Security 领域通过 `ConnectorKernel` 调用 Gravitino connector
- [x] 删除业务层对 `AirflowClient/GravitinoClient` 的直接依赖
- [x] 不新增 `*ConnectorService` 中间命名层，保持现有 `*Service/*BizService`
- [x] 删除 `RoleServiceImpl` 中遗留的 `UnsupportedOperationException` 分支

#### G. 前端接口收口与调用治理

前端接口调用清单（页面 -> service -> endpoint）：

| 页面/模块 | Service | Endpoint |
| --- | --- | --- |
| Setup | `setupService` | `API_BASE.studioSetup + API_PATH.setup.*` |
| Login / Workspace / Invite | `authService` + `studioInvitationService` | `API_BASE.login` + `API_BASE.studioBiz` |
| Project | `studioProjectService` | `API_BASE.studioBiz + API_PATH.project.*` |
| Workflow Studio | `studioWorkflowService` + `aiWorkflowService` | `API_BASE.studioBiz + API_PATH.workflow.*` 和 `API_BASE.aiWorkflow + API_PATH.workflow.chat/sse/abort` |
| Governance Metadata | `oneMetaService` | `API_BASE.governanceMetadata + 领域路径` |
| Governance Semantic | `oneMetaSemanticService` | `API_BASE.governanceSemantic + 领域路径` |
| Knowledge Graph/Wiki | `knowledgeGraphService` + `knowledgeWikiService` | `API_BASE.aiKnowledge` + `API_BASE.aiKnowledgeWiki` |
| SQL IDE | `sqlService` | `API_BASE.studioSql + API_PATH.sql.execute` |
| Tenant Admin / Role / Member / Profile | `studioTenantAdminService` + `studioTenantRoleService` + `studioUserProfileService` | `API_BASE.studioAdmin` + `API_BASE.studioBiz` |
| LLM 管理与测试 | `studioLlmService` + `aiLlmPlaygroundService` | `API_BASE.studioAdmin` + `API_BASE.studioBiz` + `API_BASE.aiLlmPlayground` |
| Health | `healthService` | `API_BASE.studioActuator + API_PATH.health.service` |

- [x] 前端梳理全部接口调用点并形成清单（页面 -> service -> endpoint）
- [x] 前端删除直连 `/api/onemeta/**` 的调用路径，统一迁移到 Studio 领域 API
- [x] 前端删除任何直连 Airflow 插件路径（`/datapillar/**`、`/plugins/datapillar/**`）
- [x] 前端删除请求体中的 `connectorId/metalake/dag_id` 路由参数
- [x] 前端统一通过 `src/api/endpoints` 暴露的领域 API 常量发请求，禁止散落硬编码 URL
- [x] 前端为接口层增加静态约束（lint/scan）：禁止新增 `/api/onemeta` 与 Airflow 插件直连
- [x] 前端将 `oneMetaService/oneMetaSemanticService` 改造为调用 Studio 语义接口，不保留引擎直通语义

#### H. Gateway 路由与鉴权配置收口

- [x] 删除 Gateway 路由配置中的 `/api/onemeta/**` 直通入口（含 Nacos 配置中心中的动态路由定义）
- [x] 删除 Gateway 路由配置中的 Airflow 插件直通入口（`/datapillar/**`、`/plugins/datapillar/**`）
- [x] Gateway 路由仅保留领域入口：`/api/studio/**`、`/api/ai/**`、`/api/auth/**`
- [x] 更新 `AuthenticationProperties` 受保护前缀，移除 `/api/onemeta` 残留配置
- [x] 校验 Gateway 鉴权过滤器仅注入可信上下文头，不使用客户端透传头参与路由决策
- [x] 更新 Gateway 文档与配置样例，删除任何引擎直通示例

#### I. 配置、打包与启动约束

- [x] 只保留 connector 实例连接参数，禁止 `operation -> connector` 动态映射
- [x] 固化 Gravitino `oneMeta/oneSemantics` 领域映射，不允许请求级覆盖
- [x] 保持 classpath 插件发现模型，新增插件需重启生效
- [x] 发行包 `libs/*` 打包链路验证通过（含 connector 插件）
- [x] 删除 RPC 一致性相关配置项（含旧开关、旧表名映射等），避免残留旧路径可达

#### J. 测试与验收

- [x] SPI/Runtime 单测覆盖：发现、校验、冲突报错、幂等、重试、超时、错误映射
- [x] Gravitino connector 单测覆盖：Metadata/Security 主流程与错误分支
- [x] Airflow connector/plugin 单测覆盖：租户头缺失、租户不匹配、越权访问拒绝
- [x] Airflow 硬切回归：新 `dag_id`、租户目录、列表过滤、旧格式拒绝
- [x] 前端回归：所有页面仅调用 Studio/AI 领域 API，无 `onemeta/airflow plugin` 直连
- [x] Gateway 回归：无 `/api/onemeta/**` 与 Airflow 插件直通路由，鉴权与头注入行为符合约束
- [x] 启动验收：无 connector 直连调用、无双轨兼容分支、无协议兜底分支
- [x] 启动验收：无 RPC 一致性路径访问日志、无 RPC 一致性写入
- [x] 全仓新增/修改的代码注释全部使用英文

#### K. 验收命令与结果（2026-03-04）

- [x] Gravitino Java Client 本地发布：`cd datapillar-gravitino && ./gradlew --no-daemon :clients:client-java:publishToMavenLocal :clients:client-java-runtime:publishToMavenLocal -x test -x javadoc`
- [x] Connector 模块单测：`mvn -pl datapillar-connector/datapillar-connector-runtime,datapillar-connector/datapillar-connector-airflow,datapillar-connector/datapillar-connector-gravitino -am test -Dmaven.repo.local=/tmp/m2`
- [x] Studio 关键回归：`mvn -pl datapillar-studio-service -Dtest=TenantServiceImplTest,SetupServiceImplTest,TenantKeyCheckTest,RoleServiceImplTest test -Dmaven.repo.local=/tmp/m2`
- [x] 前端 API 约束回归：`npm --prefix web/datapillar-studio run test -- tests/lib/api/endpoints.test.ts tests/lib/api/forbiddenRoutes.test.ts`
- [x] 前端类型检查：`npm --prefix web/datapillar-studio run type-check`
- [x] Airflow plugin 回归：`python3 datapillar-airflow-plugin/tests/test_dag_generator.py`
- [x] 发行包链路：`mvn -pl datapillar-distribution -am package -Dmaven.test.skip=true -Dmaven.repo.local=/tmp/m2`
- [x] 打包结果验证（`datapillar-distribution/target/datapillar-1.0.0/libs`）：包含 `datapillar-connector-runtime-1.0.0.jar`、`datapillar-connector-spi-1.0.0.jar`、`datapillar-connector-airflow-1.0.0.jar`、`datapillar-connector-gravitino-1.0.0.jar`、`gravitino-client-java-1.0.0.jar`

---

## 16. 明确禁止的反模式

1. 前端或网关直连下游开源系统 API。
2. 业务服务直接依赖下游 HTTP client。
3. Kernel 内按 connector 类型写业务分支。
4. 用配置做请求级 `operation -> connector` 动态路由。
5. 在 connector 和 kernel 两层重复实现重试/超时，产生双重策略。
6. 新增 connector 必须改 SPI 接口。
7. 前端传 `metalake` 或在 URL 中写死 `metalake` 作为业务路由输入。
8. 用单一全局 `gravitino.metalake` 覆盖所有领域（metadata + semantic）。
9. 在 Airflow Connector 内引入非 HTTP 协议分支（SDK/本地直调）导致双实现。
10. Airflow 继续使用 `datapillar_project_*` 或其他不含租户编码的 DAG 命名。
11. 禁止 Airflow DAG 文件平铺在 `dags_folder` 根目录；必须按租户目录隔离。
12. 允许 `tenantCode` 可变或不做规范化，导致 `dag_id`、目录、幂等键漂移。
13. 未执行 `client-java` 本地发布就直接联调 Gravitino Connector。
14. 前端直连 `/api/onemeta/**` 或 Airflow 插件路径（`/datapillar/**`、`/plugins/datapillar/**`）。
15. Gateway 保留 `/api/onemeta/**` 或 Airflow 插件直通路由，绕过领域 API 收口。
16. 为 connector 新增额外幂等业务表或继续复用 RPC 一致性表/访问层，导致语义耦合与维护复杂度上升。

---

## 17. Flink 对齐证据（修订后）

源码与文档参考：

1. `Factory` SPI：
   <https://github.com/apache/flink/blob/master/flink-table/flink-table-common/src/main/java/org/apache/flink/table/factories/Factory.java>
2. `DynamicTableFactory` 上下文与 forward options：
   <https://github.com/apache/flink/blob/master/flink-table/flink-table-common/src/main/java/org/apache/flink/table/factories/DynamicTableFactory.java>
3. `FactoryUtil` 辅助校验与发现：
   <https://github.com/apache/flink/blob/master/flink-table/flink-table-common/src/main/java/org/apache/flink/table/factories/FactoryUtil.java>
4. `FactoryUtilTest` 冲突/缺失/未消费选项错误语义：
   <https://github.com/apache/flink/blob/master/flink-table/flink-table-common/src/test/java/org/apache/flink/table/factories/FactoryUtilTest.java>
5. 官方自定义 connector 文档：
   <https://github.com/apache/flink/blob/master/docs/content/docs/dev/table/sourcesSinks.md>

对齐关系：

1. Flink `factoryIdentifier()` -> Datapillar `connectorIdentifier()`
2. Flink `requiredOptions/optionalOptions` -> Datapillar 启动期配置契约
3. Flink `FactoryUtil` -> Datapillar `ConnectorFactoryHelper` 校验策略
4. Flink SPI 发现 + 冲突报错 -> Datapillar 启动失败快速暴露插件问题

---

## 18. 最终结论

修订后的 Connector 架构是：

1. **不是网关直连下游**，而是 Studio 领域语义统一入口。
2. **不是请求级动态路由**，而是领域固定绑定 + 内部 `connectorId` 调度。
3. **不是口号式 SPI**，而是带启动期校验、冲突报错、未消费选项校验的可执行契约。
4. **不是空幂等字段**，而是稳定幂等键 + 下游幂等语义，并彻底删除 RPC 一致性路径。
5. **不是单 metalake 混跑**，而是每租户固定双 metalake：`oneMeta` + `oneSemantics`。

这样才能真正屏蔽底层差异，而不是把差异换个地方继续泄漏。
