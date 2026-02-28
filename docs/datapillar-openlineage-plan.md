# datapillar-openlineage 计划文档

## 1. 项目定位

- 这是一个独立的 OpenLineage **Sink** 微服务，不承担 Source 职责。
- 职责边界：接收 OpenLineage 事件、做租户隔离校验、持久化原始事件、写入 Neo4j 资产图谱。
- 本服务只负责 **ingest**，不提供血缘/资产查询接口。
- 技术基线与 `datapillar-studio-service` 对齐：Spring Boot + Nacos + Security + Actuator + MyBatis 风格分层。
- 服务上下文路径固定为：`/api/openlineage`（不使用 `/v1`）。

## 2. 完整目录结构（Marquez 对齐版）

```text
datapillar-openlineage/
  pom.xml
  src/
    main/
      java/
        com/sunny/datapillar/openlineage/
          DatapillarOpenLineageApplication.java

          api/
            OpenLineageApi.java
            dto/
              IngestAckResponse.java

          config/
            SecurityConfig.java
            OpenLineageJacksonConfig.java
            OpenLineageExecutorConfig.java
            MyBatisPlusConfig.java
            Neo4jConfig.java

          security/
            OpenLineageAuthFilter.java
            TenantResolver.java
            TenantContext.java
            TenantContextHolder.java

          service/
            OpenLineageService.java
            impl/
              OpenLineageServiceImpl.java

          dao/
            OpenLineageDao.java
            OpenLineageEventDao.java
            OpenLineageGraphDao.java
            mapper/
              OpenLineageEventMapper.xml

          model/
            OpenLineageEventEnvelope.java
            OpenLineageUpdateResult.java

          exception/
            OpenLineageValidationException.java
            OpenLineageTenantMismatchException.java
            OpenLineageWriteException.java

      resources/
        application.yml
        db/migration/
          V1__create_lineage_events.sql

    test/
      java/
        com/sunny/datapillar/openlineage/
          api/
            OpenLineageApiTest.java
          service/
            OpenLineageServiceTest.java
          dao/
            OpenLineageDaoTest.java
          security/
            TenantResolverTest.java
```

## 3. 技术实现思考（严格按 Marquez 主链路）

### 3.1 接口设计（单入口）

- `POST /api/openlineage`
- `OpenLineageApi` 仅一个 ingest 接口，方法职责与 Marquez `OpenLineageResource#create` 对齐：
  - 解析事件类型（`LineageEvent` / `DatasetEvent` / `JobEvent`）
  - 调用 `openLineageService.createAsync(...)`
  - `whenComplete(...)` 返回状态码（成功 `201`，参数错误 `400`，其他 `500`）
- 不做 `/v1`、不拆多接口。
- 不提供查询型接口（如 lineage query / graph query）。

### 3.2 处理链路（createAsync 双写并行）

1. `OpenLineageAuthFilter` 做网关断言验签。
2. `TenantResolver` 按 source 类型做双模式租户解析（Gravitino=facet tenant，Compute=auth tenant）并执行一致性校验。
3. `OpenLineageService#createAsync(...)` 并行执行两条 Future（与 Marquez 一致）：
   - `openlineage` 分支：`OpenLineageDao.create*Event(...)` 持久化原始事件到 MySQL `lineage_events`
   - `datapillar` 分支：`OpenLineageDao.updateDatapillarModel(...)` 更新 Neo4j 资产图谱
4. `CompletableFuture.allOf(...)` 汇总结果，API 端统一回包。
5. 主链路成功后进入 Task Tracking：`enqueue -> push Worker`，异步执行摘要/向量并在任务层更新状态。

### 3.3 租户隔离（sink 侧硬约束）

- Source 端（Gravitino、Spark、Flink、Hive 等）不要求、也不假设具备租户隔离能力。
- 租户解析采用按 source 类型的双模式策略：
  - `GRAVITINO`（tenant-aware source）：以 `gravitino facet tenant*` 为归属锚点。
  - `COMPUTE_ENGINE`（Spark/Flink/Hive，tenant-unaware source）：以网关鉴权租户为归属锚点。
- 校验规则：
  - Gravitino 事件必须带 facet tenant；缺失直接拒绝（或进入 dead letter）。
  - Gravitino 事件若网关上下文携带 tenant，则必须与 facet tenant 一致；不一致拒绝。
  - 计算引擎事件允许 facet tenant 缺失，使用鉴权租户归属。
  - 计算引擎事件若携带 facet tenant，则必须与鉴权租户一致；不一致拒绝。
  - 无法得到任何租户信号（facet 与 auth 同时缺失）直接拒绝（或进入 dead letter）。
- MySQL 隔离规则：
  - `lineage_events` 每行强制写入 `tenant_id/tenant_code/tenant_name`。
  - 幂等与去重键必须包含 `tenant_id` 维度。
- Neo4j 隔离规则：
  - 所有节点写入 `tenantId` 属性。
  - 所有 `MERGE/MATCH` 必须带 `tenantId` 条件。
  - 关系边只允许在同一 `tenantId` 内建立，禁止跨租户连边。

### 3.4 租户隔离职责矩阵（职责标记）

| 组件 | 责任 | 禁止事项 |
|---|---|---|
| Source(Gravitino/Flink/Spark/Hive) | 通过 OpenLineage HTTP transport 上报事件与凭证 | 自行决定最终租户归属、绕过网关直连 sink |
| `OpenLineageAuthFilter` | 解析并验证 source 身份与鉴权租户上下文 | 直接用 source 字段覆盖网关鉴权结果 |
| `TenantResolver` | 按 source 类型解析 tenant（Gravitino=facet，Compute=auth）并做一致性比对 | 在 tenant 信号缺失时继续写入 |
| `OpenLineageApi` | 只做 ingest 编排与错误码返回 | 提供查询接口 |
| `OpenLineageService` | 并行触发原始事件落库与资产写入 | 把异步任务状态写进图谱 |
| `OpenLineageEventDao` | 以 tenant 维度落 `lineage_events` | 无 tenant 条件写入 |
| `OpenLineageGraphDao` | 以 tenant 维度 upsert 节点和边 | 跨租户 `MERGE/MATCH` / 跨租户连边 |
| `Async Worker` | tenant 维度执行摘要/向量并回写 | 无 tenant 条件回写节点 |
| Task Tracking(MySQL) | 记录任务状态/重试/追踪 | 将任务状态字段写入 Neo4j |

### 3.5 存储模型（首版）

- MySQL：仅保留 Marquez 风格事件主表 `lineage_events`（原始事件事实表）。
- Neo4j：资产图谱写入（Tenant -> Catalog -> Schema -> Table -> ...）。
- `ol_event_inbox` / `ol_event_idempotency` / `ol_event_dead_letter` 不作为首版主链路。

### 3.6 采集启动顺序（前端驱动）

1. 前端先配置租户向量模型（provider/model/dimension/endpoint/credentialRef）。
2. 前端手动触发 Gravitino 全量同步，先建立租户资产基线图谱。
3. 全量同步完成后将租户状态切到 `READY`。
4. 只有 `READY` 租户才开启实时 OpenLineage 事件接入。

### 3.7 切换窗口与不漏数规则

- 启动全量同步时记录 `cutover_time`。
- 实时链路仅处理 `event_time >= cutover_time` 的事件。
- 同步期间到达的事件先按 `event_time` 缓存入 `lineage_events`，切换完成后按时间窗口回放。

### 3.8 Source 上报身份要求（基于 OpenLineage 官方能力）

- 结论：SQL 本身无法标识租户，租户身份必须绑定在 OpenLineage 上报通道。
- 本服务接收的 Source 范围包括：
  - 元数据 Source：Gravitino
  - 计算执行 Source：Spark / Flink / Hive 等
- OpenLineage 官方 HTTP transport 支持鉴权配置：
  - `auth.type=api_key`（Bearer API Key）
  - `auth.type=jwt`（通过 token endpoint 交换 JWT）
  - 也支持自定义 `headers`
- Datapillar 当前定稿选型：
  - Source（Gravitino / Spark / Flink / Hive）统一使用 `auth.type=api_key` 上报到网关。
  - Source 不直连 `datapillar-openlineage`，固定走网关入口。
  - 网关完成 `api_key -> source_identity(+可选tenant)` 映射后，再转发到 sink。
- Datapillar 约束：
  - Gravitino 场景允许共享同一服务凭证（来源鉴权），租户归属由事件 facet tenant 决定。
  - 计算引擎场景必须每租户独立上报凭证，由网关映射鉴权租户归属。
  - sink 按 source 类型执行归属规则：Gravitino=facet tenant；Compute=auth tenant。
- 职责标记：
  - Source(Gravitino/Flink/Spark/Hive)：负责带上凭证上报，不负责最终租户隔离判定。
  - Gateway/Auth：负责凭证鉴权、source 身份识别与上下文签名透传。
  - datapillar-openlineage(sink)：负责最终租户归属判定与隔离写入执行。

## 4. 技术实现细化（代码级对齐）

### 4.1 类职责对齐表

- `OpenLineageApi` ≈ Marquez `OpenLineageResource`
- `OpenLineageService` ≈ Marquez `OpenLineageService`
- `OpenLineageDao` ≈ Marquez `OpenLineageDao`（保留 `create*Event` + `update*Model` 形态）

### 4.2 Service 方法形态（必须同构）

- `CompletableFuture<Void> createAsync(LineageEvent event)`
- `CompletableFuture<Void> createAsync(DatasetEvent event)`
- `CompletableFuture<Void> createAsync(JobEvent event)`
- 每个方法都固定：
  - `CompletableFuture.runAsync(() -> dao.create*Event(...), executor)`
  - `CompletableFuture.runAsync(() -> dao.updateDatapillarModel(...), executor)`
  - `return CompletableFuture.allOf(datapillar, openlineage);`

### 4.3 Dao 方法形态（必须同构）

- 事件写入：
  - `createLineageEvent(...)`
  - `createDatasetEvent(...)`
  - `createJobEvent(...)`
- 模型更新：
  - `updateDatapillarModel(LineageEvent event, ObjectMapper mapper)`
  - `updateDatapillarModel(DatasetEvent event, ObjectMapper mapper)`
  - `updateDatapillarModel(JobEvent event, ObjectMapper mapper)`
- 其中 `updateDatapillarModel*` 内部做：
  - facet 归一化
  - Tenant 节点 upsert
  - 资产节点/关系 upsert（全部 tenant 作用域）

### 4.4 与 studio-service 的技术一致性

- Spring Boot + Nacos + Security + Actuator 基线保持一致。
- `spring.application.name = datapillar-openlineage`
- `server.servlet.context-path = /api/openlineage`
- 网关路由：`Path=/api/openlineage/**` -> `lb://datapillar-openlineage`

### 4.5 MySQL 原始事件表设计（lineage_events）

- 采用 Marquez 同构思路：`lineage_events` 作为原始事件事实表。
- Datapillar 差异：新增租户字段 `tenant_id`、`tenant_code`、`tenant_name`。
- 写入语义：
  - `createLineageEvent(...)` 写 `_event_type='RUN_EVENT'`
  - `createDatasetEvent(...)` 写 `_event_type='DATASET_EVENT'`
  - `createJobEvent(...)` 写 `_event_type='JOB_EVENT'`

```sql
CREATE TABLE lineage_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '物理主键',

  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
  tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',

  event_time DATETIME(6) NOT NULL COMMENT 'OpenLineage事件时间(UTC)',
  event_type VARCHAR(32) NULL COMMENT 'RUN_EVENT里的START/COMPLETE/FAIL/ABORT',
  run_uuid CHAR(36) NULL COMMENT 'run uuid(仅RUN_EVENT通常有值)',
  job_name VARCHAR(255) NULL COMMENT '作业名',
  job_namespace VARCHAR(255) NULL COMMENT '作业命名空间',

  producer VARCHAR(512) NULL COMMENT '事件生产者',
  _event_type VARCHAR(64) NOT NULL DEFAULT 'RUN_EVENT' COMMENT 'RUN_EVENT/DATASET_EVENT/JOB_EVENT',
  event JSON NOT NULL COMMENT '完整OpenLineage原始事件',

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '入库时间',

  KEY idx_le_tenant_time (tenant_id, event_time DESC),
  KEY idx_le_tenant_event_type_time (tenant_id, _event_type, event_time DESC),
  KEY idx_le_tenant_run_time (tenant_id, run_uuid, event_time DESC),
  KEY idx_le_tenant_job_time (tenant_id, job_namespace, job_name, event_time DESC),
  KEY idx_le_created_at (created_at DESC),
  CHECK (_event_type IN ('RUN_EVENT', 'DATASET_EVENT', 'JOB_EVENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenLineage原始事件表';
```

### 4.6 向量化执行策略（事件驱动异步批量，不扫描 Neo4j）

- 事件实时链路只做资产写入，不做同步向量化调用。
- 写入成功后由 `OpenLineageService` enqueue 向量候选到 `ol_async_task`（`task_type=EMBEDDING`，upsert 幂等键）。
- 此处“push”指：写任务表后立即把 `taskId` 推给 Worker 执行通道（push-first）。
- 主执行路径：Worker 直接消费 push 到达的任务并执行向量化。
- 恢复路径：仅在进程重启/Worker 崩溃/任务超时时，Worker 才从 MySQL 拉取 `PENDING/FAILED` 任务补偿执行（db-pull）。
- 批处理维度：按 `tenant + model` 聚合微批（例如 32/64）。
- 向量结果仅回写资产节点业务字段：`embedding/embeddingUpdatedAt/embeddingProvider`。
- 明确约束：
  - API 请求线程不等待向量化。
  - API `201` 只代表事件入库 + 图谱更新成功。
  - 不新增专用任务表 `ol_embedding_tasks`，不扫描 Neo4j，不在知识图谱里引入任务状态机字段。

### 4.7 SQL 摘要执行策略（事件驱动异步批量，不扫描 Neo4j）

- 实时事件链路只写 SQL 资产节点（`content/dialect/engine/job*`），不在请求线程调用 LLM。
- SQL 节点写入成功后，enqueue 摘要候选到 `ol_async_task`（`task_type=SQL_SUMMARY`，upsert 幂等键）。
- 主执行路径：Worker 直接消费 push 到达的 SQL 摘要任务（push-first）。
- 恢复路径：仅在异常恢复场景下从 MySQL 拉取 `PENDING/FAILED` 摘要任务补偿执行（db-pull）。
- 按 `tenant + summaryModel` 聚合微批，先批量生成 `summary/tags`，再批量 embedding。
- 结果直接回写同一 `SQL` 节点：`summary/tags/summaryGeneratedAt/embedding/embeddingUpdatedAt`。
- 明确约束：
  - 不新增专用任务表 `ol_sql_summary_tasks`。
  - 不在知识图谱里增加任务状态字段（`PENDING/RUNNING/...` 这类全部不落图）。
  - 摘要失败不阻塞血缘主链路，通过任务子系统重试或后续实时事件再次触发。

### 4.8 Python 存量能力补齐矩阵（Java 必须覆盖）

- 操作矩阵必须完整覆盖，不允许只支持部分 operation。

| operation | 元数据节点写入 | 关系写入 | 删除语义 |
|---|---|---|---|
| `create_table` / `load_table` | Catalog/Schema/Table/Column upsert | `HAS_SCHEMA/HAS_TABLE/HAS_COLUMN` + 可选 SQL 血缘 | - |
| `alter_table` | 表/列细粒度动作（rename/add/delete/update） | 结构边增量维护 + 可选 SQL 血缘 | 列删除、重命名按节点级生效 |
| `drop_table` | 删除 Table | 清理相关边 | 级联删除 Table+Column |
| `create_schema` / `alter_schema` / `load_schema` | Catalog/Schema upsert | `HAS_SCHEMA` | - |
| `drop_schema` | 删除 Schema | 清理相关边 | 级联删除 Schema+Table+Column+Metric |
| `create_catalog` / `alter_catalog` | Catalog upsert | - | - |
| `drop_catalog` | 删除 Catalog | 清理相关边 | 级联删除 Catalog+Schema+Table+Column+Metric |
| `register_metric` / `alter_metric` | Atomic/Derived/CompositeMetric upsert | `HAS_METRIC` + `DERIVED_FROM/COMPUTED_FROM` + `MEASURES/FILTERS_BY` | - |
| `drop_metric` | 删除 Metric | 清理相关边 | 节点级删除 |
| `create_wordroot` / `alter_wordroot` | WordRoot upsert | - | - |
| `drop_wordroot` | 删除 WordRoot | 清理相关边 | 节点级删除 |
| `create_modifier` / `alter_modifier` | Modifier upsert | - | - |
| `drop_modifier` | 删除 Modifier | 清理相关边 | 节点级删除 |
| `create_unit` / `alter_unit` | Unit upsert | - | - |
| `drop_unit` | 删除 Unit | 清理相关边 | 节点级删除 |
| `create_valuedomain` / `alter_valuedomain` | ValueDomain upsert | - | - |
| `drop_valuedomain` | 删除 ValueDomain | 清理相关边 | 节点级删除 |
| `create_tag` / `alter_tag` | Tag upsert | - | - |
| `drop_tag` | 删除 Tag | 清理相关边 | 节点级删除 |
| `associate_tags` | - | `vd:* -> HAS_VALUE_DOMAIN`；普通 tag -> `HAS_TAG` | 删除关联边，不删节点 |

## 5. Neo4j 数据模型（定稿）

### 5.1 模型边界

- 仅做资产图谱，不新增 `OLJob` / `OLRun` / `OLEvent`。
- 保留现有资产与语义节点体系，在此基础上加入 `Tenant` 根节点。
- `Tenant` 节点承载租户主数据：`tenantId`、`tenantCode`、`tenantName`。

### 5.2 节点定义

- `Tenant`
  - `id`(Long, tenantId)
  - `code`(String, tenantCode)
  - `name`(String, tenantName)
  - `createdAt`, `updatedAt`
- `Catalog:Knowledge`
  - `id`, `tenantId`, `name`, `metalake`, `catalogType`, `provider`
  - `description`, `properties`, `createdAt`, `updatedAt`
- `Schema:Knowledge`
  - `id`, `tenantId`, `name`, `description`, `properties`, `createdAt`, `updatedAt`
- `Table:Knowledge`
  - `id`, `tenantId`, `name`, `producer`, `description`, `properties`
  - `partitions`, `distribution`, `sortOrders`, `indexes`
  - `creator`, `createTime`, `lastModifier`, `lastModifiedTime`
  - `createdAt`, `updatedAt`
- `Column:Knowledge`
  - `id`, `tenantId`, `name`, `dataType`, `description`
  - `nullable`, `autoIncrement`, `defaultValue`, `createdAt`, `updatedAt`
- `SQL:Knowledge`
  - `id`, `tenantId`, `content`, `dialect`, `engine`, `jobNamespace`, `jobName`
  - `summary`, `tags`, `embedding`, `createdAt`, `updatedAt`
- `AtomicMetric:Knowledge` / `DerivedMetric:Knowledge` / `CompositeMetric:Knowledge`
  - `id`, `tenantId`, `code`, `name`, `description`, `unit`
  - `aggregationLogic`, `calculationFormula`, `createdAt`, `updatedAt`
- `WordRoot:Knowledge`
  - `id`, `tenantId`, `code`, `name`, `dataType`, `description`, `createdAt`, `updatedAt`
- `Modifier:Knowledge`
  - `id`, `tenantId`, `code`, `name`, `modifierType`, `description`, `createdAt`, `updatedAt`
- `Unit:Knowledge`
  - `id`, `tenantId`, `code`, `name`, `symbol`, `description`, `createdAt`, `updatedAt`
- `ValueDomain:Knowledge`
  - `id`, `tenantId`, `code`, `name`, `domainType`, `domainLevel`, `items`
  - `description`, `createdAt`, `updatedAt`
- `Tag:Knowledge`
  - `id`, `tenantId`, `name`, `description`, `createdAt`, `updatedAt`

### 5.3 关系定义

- 租户归属边：
  - `(Tenant)-[:OWNS_CATALOG]->(Catalog)`
  - `(Tenant)-[:OWNS_SQL]->(SQL)`
  - `(Tenant)-[:OWNS_TAG]->(Tag)`
  - `(Tenant)-[:OWNS_METRIC]->(AtomicMetric|DerivedMetric|CompositeMetric)`
  - `(Tenant)-[:OWNS_WORDROOT]->(WordRoot)`
  - `(Tenant)-[:OWNS_MODIFIER]->(Modifier)`
  - `(Tenant)-[:OWNS_UNIT]->(Unit)`
  - `(Tenant)-[:OWNS_VALUEDOMAIN]->(ValueDomain)`
- 资产层级边：
  - `(Catalog)-[:HAS_SCHEMA]->(Schema)`
  - `(Schema)-[:HAS_TABLE]->(Table)`
  - `(Table)-[:HAS_COLUMN]->(Column)`
  - `(Schema)-[:HAS_METRIC]->(Metric)`
- 血缘与语义边：
  - `(Table)-[:INPUT_OF]->(SQL)`
  - `(SQL)-[:OUTPUT_TO]->(Table)`
  - `(Column)-[:DERIVES_FROM]->(Column)`
  - `(AtomicMetric)-[:MEASURES]->(Column)`
  - `(AtomicMetric)-[:FILTERS_BY]->(Column)`
  - `(DerivedMetric|CompositeMetric)-[:DERIVED_FROM|COMPUTED_FROM]->(AtomicMetric)`
  - `(Column)-[:HAS_VALUE_DOMAIN]->(ValueDomain)`
  - `(AnyAsset)-[:HAS_TAG]->(Tag)`

### 5.4 约束定义（租户内唯一）

> 不再使用“全局 `id` 唯一”，统一改为“`tenantId + id` 复合唯一”。

```cypher
CREATE CONSTRAINT tenant_id_uk IF NOT EXISTS
FOR (t:Tenant) REQUIRE t.id IS UNIQUE;

CREATE CONSTRAINT tenant_code_uk IF NOT EXISTS
FOR (t:Tenant) REQUIRE t.code IS UNIQUE;

CREATE CONSTRAINT catalog_tenant_id_uk IF NOT EXISTS
FOR (n:Catalog) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT schema_tenant_id_uk IF NOT EXISTS
FOR (n:Schema) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT table_tenant_id_uk IF NOT EXISTS
FOR (n:Table) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT column_tenant_id_uk IF NOT EXISTS
FOR (n:Column) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT sql_tenant_id_uk IF NOT EXISTS
FOR (n:SQL) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT atomic_metric_tenant_id_uk IF NOT EXISTS
FOR (n:AtomicMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT derived_metric_tenant_id_uk IF NOT EXISTS
FOR (n:DerivedMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT composite_metric_tenant_id_uk IF NOT EXISTS
FOR (n:CompositeMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT wordroot_tenant_id_uk IF NOT EXISTS
FOR (n:WordRoot) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT modifier_tenant_id_uk IF NOT EXISTS
FOR (n:Modifier) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT unit_tenant_id_uk IF NOT EXISTS
FOR (n:Unit) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT valuedomain_tenant_id_uk IF NOT EXISTS
FOR (n:ValueDomain) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT tag_tenant_id_uk IF NOT EXISTS
FOR (n:Tag) REQUIRE (n.tenantId, n.id) IS UNIQUE;
```

### 5.5 事件到租户模型映射规则

- 按 source 类型映射 tenant：
  - Gravitino 事件：tenant 来源于 `gravitino facet` 的 `tenantId/tenantCode/tenantName`。
  - 计算引擎事件：tenant 来源于网关鉴权上下文（由 api_key 映射）。
- 每次写资产前先写租户节点：
  - `MERGE (t:Tenant {id: tenantId})`
  - `SET t.code = tenantCode, t.name = tenantName, t.updatedAt = datetime()`
- 所有资产节点同时写入 `tenantId` 属性，并建立对应 `OWNS_*` 关系。

### 5.6 索引策略（检索与向量召回）

- 约束索引：全部使用 `tenantId + id` 复合唯一约束（Tenant 自身除外）。
- 向量索引：
  - `Knowledge(embedding)` 统一向量索引（主检索入口）。
  - 对 `SQL`、`Table`、`Column`、`Metric`、`Tag` 可按需建局部向量索引。
  - 维度与相似度函数由租户模型配置驱动，禁止硬编码。
- 全文索引：
  - `Knowledge` 统一全文索引覆盖 `name/description/summary/content/tags/code/items`。
  - SQL 维度单独全文索引覆盖 `content/summary/tags`。
- 职责说明：
  - 本节仅定义 Neo4j 数据侧索引能力，不代表 `datapillar-openlineage` 对外提供查询接口。

## 6. Python 现状补齐项（文档已纳入）

### 6.1 已补齐的关键能力点

- SQL 摘要异步链路：批量 LLM 摘要 + 批量 embedding + 回写 SQL 节点。
- Spark/Flink 通用事件补齐：`symlinks` + `job.namespace` 推导逻辑表，确保节点可匹配。
- `alter_table` 细粒度动作：`RENAME_TABLE/ADD_COLUMN/DELETE_COLUMN/RENAME_COLUMN/UPDATE_*`。
- `associate_tags` 分流：`vd:` 标签走 `HAS_VALUE_DOMAIN`，普通标签走 `HAS_TAG`。
- drop 级联语义：`drop_schema/drop_catalog` 级联删除资产子树和相关边。
- SQL 节点执行次数：`executionCount` 持续累加，保留热点 SQL 语义。
- 模型切换补偿：基于 `embeddingProvider` 检测 stale embedding，触发重算。

### 6.2 当前方案不保留的 Python 设计

- 不保留 Python 内存队列作为长期形态。
- Java 侧保持“Marquez 主链路 + 事件驱动批处理 Worker”，不做图谱扫描。
- 任务状态与追踪如果需要持久化，独立走 MySQL 子系统，不与知识图谱混写。

## 7. Java 代码级解决方案（Marquez 同构 + Datapillar 增强）

### 7.0 分层架构（硬分层约束）

- 已明确去掉两项错误设计：
  - 不做周期性扫描 Neo4j。
  - 不把任务状态写入知识图谱。
- 采用四层硬分层，按顺序单向调用。

第一层：`Ingress Layer`（接入编排层）

- 组件：`OpenLineageApi`、`OpenLineageAuthFilter`、`TenantResolver`。
- 职责：鉴权、租户一致性校验、事件类型分发、调用服务层。
- 禁止：直接写 MySQL / Neo4j。

第二层：`Core Sync Layer`（同步主链路层）

- 组件：`OpenLineageService#createAsync`、`OpenLineageDao`。
- 职责：并行完成两件事：
  - 写 MySQL `lineage_events` 原始事件。
  - 写 Neo4j 资产图谱核心数据。
- 返回语义：`201` 仅表示主链路成功，不包含摘要/向量完成语义。

第三层：`Task Tracking Layer`（任务追踪层，独立持久化）

- 组件：`ol_async_task`、`ol_async_task_attempt`、`ol_async_batch`。
- 职责：负责 enqueue（创建任务）与 update（更新状态/attempt/batch）。
- 存储：仅 MySQL。
- 禁止：任务状态字段写入 Neo4j。

第四层：`Async Capability Layer`（异步能力层，事件驱动）

- 组件：`EmbeddingTaskWorker`、`SqlSummaryWorker`、内存执行通道。
- 职责：消费任务并执行摘要/向量，回写资产业务字段。
- 触发方式：push-first；恢复场景使用 db-pull。
- 禁止：在请求线程内执行 LLM/Embedding。

分层调用规则：

1. 主执行链路固定为：`Ingress -> Core Sync -> Task Tracking(enqueue) -> Async Capability(execute) -> Task Tracking(update)`。
2. 恢复补偿链路固定为：`Task Tracking(select pending/failed) -> Async Capability(execute) -> Task Tracking(update)`。
3. `Task Tracking` 只管理任务元数据，不允许反向驱动图谱结构变更。
4. 任一层失败不得污染上层语义边界。

失败语义规则：

1. 主链路失败：接口按错误码返回，不创建异步任务。
2. 主链路成功、异步失败：任务层记录失败并重试，不影响主链路成功语义。
3. 任务系统故障：不影响主链路入库与入图。

### 7.1 主链路（严格同构 Marquez）

1. `OpenLineageApi` 单入口 `POST /api/openlineage`。
2. `OpenLineageService#createAsync(...)` 双 Future 并行：
   - `create*Event(...)`：写 MySQL `lineage_events` 原始事件。
   - `updateDatapillarModel(...)`：更新 Neo4j 资产图谱。
3. `CompletableFuture.allOf(...)` 汇总并回包 `201/400/500`。

这条链路保持与 Marquez 同构，不引入额外事件中间件。

### 7.2 Datapillar 增强链路（异步批处理）

- `EmbeddingTaskWorker`：
  - 主路径消费 push 的 `task_type=EMBEDDING` 任务；仅恢复路径才从 `ol_async_task` 拉取。
  - 按 `tenant + embedding model` 微批执行。
  - 批量回写节点 `embedding/embeddingUpdatedAt`。
- `SqlSummaryWorker`：
  - 主路径消费 push 的 `task_type=SQL_SUMMARY` 任务；仅恢复路径才从 `ol_async_task` 拉取。
  - 按 `tenant + summary model` 微批执行。
  - 先生成 `summary/tags`，再批量 embedding，最后写回 SQL 节点。

一致性定义：

- 图谱主数据：强一致（请求成功即可查）。
- 摘要/向量：最终一致（异步完成）。

### 7.3 多租户与模型治理

- 租户模型配置来源：前端显式配置，后端只读取租户已启用模型。
- 禁止“默认模型兜底”。
- 批处理候选生成与资产写回都必须带租户作用域。
- 模型切换后：
  - 由前端触发一次该租户全量同步重跑，重建摘要与向量。

### 7.4 与 studio-service 的微服务一致性

- 工程基线与 `datapillar-studio-service` 保持一致：
  - Spring Boot 配置体系。
  - Nacos 服务注册与配置中心。
  - Security + 网关断言链路。
  - Actuator 指标暴露与健康检查。
- 部署形态：标准独立微服务，不与 `datapillar-ai` 进程耦合。

## 8. 任务状态与追踪子系统（独立于资产图谱）

### 8.1 设计边界（硬约束）

- 任务状态只落 MySQL，不落 Neo4j。
- Neo4j 只保留资产与血缘业务字段，不混入任务生命周期字段。
- 任务子系统只管理“异步批量执行过程”，不改变主链路（`lineage_events + 资产图谱写入`）。

### 8.2 生命周期状态机

- `PENDING`：任务已创建，待执行。
- `RUNNING`：任务已被 Worker 认领执行中。
- `SUCCEEDED`：任务成功，资产已回写。
- `FAILED`：单次失败，待重试。
- `DEAD`：超过最大重试次数，停止自动重试。
- `CANCELED`：人工取消。

### 8.3 MySQL 表设计

任务主表：

```sql
CREATE TABLE ol_async_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
  task_type VARCHAR(32) NOT NULL COMMENT 'EMBEDDING/SQL_SUMMARY',

  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',

  resource_type VARCHAR(32) NOT NULL COMMENT 'SQL/TABLE/COLUMN/METRIC/TAG...',
  resource_id VARCHAR(128) NOT NULL COMMENT '资源ID(业务ID/Neo4j节点ID)',

  content_hash CHAR(64) NOT NULL COMMENT '输入内容哈希',
  model_fingerprint VARCHAR(256) NOT NULL COMMENT 'provider:model:version',

  status VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/DEAD/CANCELED',
  priority INT NOT NULL DEFAULT 100 COMMENT '优先级(越小越高)',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  max_retry INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
  next_run_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次执行时间',

  claim_token VARCHAR(64) NULL COMMENT '认领令牌',
  claim_until DATETIME(6) NULL COMMENT '认领过期时间',
  last_error VARCHAR(1024) NULL COMMENT '最近错误信息',

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

  UNIQUE KEY uq_task_dedup (
    task_type,
    tenant_id,
    resource_type,
    resource_id,
    model_fingerprint,
    content_hash
  ),
  KEY idx_task_poll (status, next_run_at, priority, id),
  KEY idx_task_tenant (tenant_id, task_type, status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务主表';
```

任务执行明细表：

```sql
CREATE TABLE ol_async_task_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  attempt_no INT NOT NULL COMMENT '第几次执行(从1开始)',

  worker_id VARCHAR(64) NOT NULL COMMENT '执行Worker实例ID',
  started_at DATETIME(6) NOT NULL COMMENT '开始时间',
  finished_at DATETIME(6) NULL COMMENT '结束时间',
  status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED/CANCELED',

  batch_no VARCHAR(64) NULL COMMENT '批次号',
  input_size INT NULL COMMENT '输入条数',
  latency_ms BIGINT NULL COMMENT '耗时(ms)',
  error_type VARCHAR(128) NULL COMMENT '错误类型',
  error_message VARCHAR(1024) NULL COMMENT '错误信息',

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  KEY idx_attempt_task (task_id, attempt_no),
  KEY idx_attempt_batch (batch_no),
  CONSTRAINT fk_attempt_task FOREIGN KEY (task_id) REFERENCES ol_async_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务执行明细';
```

批次追踪表：

```sql
CREATE TABLE ol_async_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '批次ID',
  batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
  task_type VARCHAR(32) NOT NULL COMMENT 'EMBEDDING/SQL_SUMMARY',

  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  model_fingerprint VARCHAR(256) NOT NULL COMMENT 'provider:model:version',
  worker_id VARCHAR(64) NOT NULL COMMENT '执行Worker实例ID',

  planned_size INT NOT NULL COMMENT '计划执行数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数',
  failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  started_at DATETIME(6) NOT NULL COMMENT '开始时间',
  finished_at DATETIME(6) NULL COMMENT '结束时间',
  status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED',

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

  UNIQUE KEY uq_batch_no (batch_no),
  KEY idx_batch_tenant_type_time (tenant_id, task_type, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步批次追踪';
```

### 8.4 执行链路（与资产图谱解耦）

1. 主链路成功写入资产后，按需要创建异步任务（写 `ol_async_task`）。
2. 创建任务后立即将 `taskId` push 到 Worker 执行通道（主执行路径）。
3. Worker 按 `tenant + model + task_type` 聚合微批执行。
4. 执行成功后仅回写 Neo4j 资产业务字段（summary/tags/embedding），然后任务改 `SUCCEEDED`。
5. 执行失败写 `ol_async_task_attempt`，并更新任务为 `FAILED` 或 `DEAD`。
6. 恢复补偿场景（重启/崩溃/超时）下，Worker 才使用 `FOR UPDATE SKIP LOCKED` 拉取 `PENDING/FAILED` 任务。

### 8.5 重试与幂等规则

- 幂等键：`task_type + tenant_id + resource_type + resource_id + model_fingerprint + content_hash`。
- 重试策略：指数退避（例如 1m/5m/15m/1h/6h）。
- `DEAD` 任务不自动重试，仅人工干预后重置为 `PENDING`。
- 任务系统故障不影响主链路可用性（主链路成功语义不变）。

## 9. 高并发设计章节（定稿）

### 9.1 并发目标与边界

- 目标：在高并发事件写入下，保证主链路稳定、租户隔离稳定、异步能力可追踪。
- 边界：主链路只负责事件入库与资产入图；摘要/向量属于异步能力层。
- 红线：不扫描 Neo4j；不把任务状态写入图谱。

### 9.2 主链路抗并发策略

1. 接入层轻量化：鉴权、租户校验、事件分发，不做重计算。
2. 服务层并行化：`createAsync` 并行执行 MySQL 原始事件写入与 Neo4j 核心图谱写入。
3. 响应语义收敛：`201` 只表示主链路成功，避免把异步能力绑进请求时延。
4. 写路径固定化：只允许追加写 `lineage_events` + tenant 作用域图谱 upsert，避免请求路径复杂分叉。

### 9.3 异步能力层抗并发策略

1. 触发方式：主链路成功后 push 异步候选，不做图谱轮询。
2. 任务持久化：统一写 `ol_async_task`，不拆 `ol_embedding_tasks/ol_sql_summary_tasks`。
3. 主执行：push-first，Worker 直接消费推送任务，避免常态轮询开销。
4. 恢复补偿：仅异常恢复场景使用 `FOR UPDATE SKIP LOCKED` 拉取任务，避免重复消费与锁等待放大。
5. 微批聚合：按 `tenant + model + task_type` 聚合批次，控制外部模型调用成本。
6. 批量回写：摘要/向量结果批量回写 Neo4j，减少往返次数。

### 9.4 幂等与去重策略

1. 主链路幂等：建议按事件指纹做去重（租户、run、eventType、eventTime、payloadHash）。
2. 异步幂等：以 `task_type + tenant_id + resource_type + resource_id + model_fingerprint + content_hash` 作为唯一幂等键。
3. 重放安全：重复事件允许进入主链路，但不会放大异步重复计算。

### 9.5 资源隔离与租户公平

1. 线程池隔离：接入线程池、主链路线程池、异步 Worker 线程池分离。
2. 连接池隔离：MySQL 与 Neo4j 连接池分离，避免互相拖垮。
3. 租户公平：任务调度按租户分组，防止单租户占满全局异步执行槽位。
4. 限流降载：租户级速率限制 + 全局背压阈值，超过阈值时优先保护主链路。

### 9.6 失败恢复与可用性

1. 主链路失败：请求返回错误，不创建异步任务。
2. 主链路成功、异步失败：任务表记录失败并退避重试，不影响主链路成功语义。
3. 重试上限：超过 `max_retry` 转 `DEAD`，避免无限重试拖垮系统。
4. 系统恢复：服务重启后依赖 MySQL 任务表恢复任务执行状态。

### 9.7 扩容策略

1. 服务实例横向扩展：无状态 API/Worker 可多实例部署。
2. Worker 并行扩展：依赖 `SKIP LOCKED` 实现多实例并发消费。
3. 数据库扩容优先级：先保障 MySQL 写入与查询索引，再优化 Neo4j 批写吞吐。

### 9.8 观测指标（高并发必备）

- 主链路：QPS、P95/P99、入库成功率、入图成功率。
- 异步链路：任务积压量、认领速率、成功率、重试率、DEAD 数量、批次耗时。
- 资源指标：线程池活跃度、队列长度、MySQL/Neo4j 连接池利用率。
- 租户维度：每租户吞吐、失败率、限流命中率。

## 10. 执行顺序 TODO（可打钩）

### 10.1 实施准备

- [x] 文档方案定稿：`sink-only`、`/api/openlineage`、`Tenant 根节点`、`push-first + db-pull仅恢复`。
- [x] 创建 `datapillar-openlineage` 工程骨架与目录（`api/service/dao/config/security`）。
- [x] 创建启动类 `DatapillarOpenLineageApplication`，并配置 `server.servlet.context-path=/api/openlineage`。
- [x] 初始化 MySQL 迁移脚本：`lineage_events`、`ol_async_task`、`ol_async_task_attempt`、`ol_async_batch`。
- [x] 初始化 Neo4j 约束脚本（`Tenant` 唯一 + 全部 `tenantId + id` 复合唯一）。

### 10.2 主执行链路（Ingress -> Core Sync -> Task Tracking(enqueue)）

- [x] 实现 `OpenLineageAuthFilter`：校验网关签名与 source 身份上下文。
- [x] 实现 `TenantResolver`：按 source 双模式归属（Gravitino=facet，Compute=auth）与一致性校验。
- [x] 实现 `OpenLineageApi` 单入口：`POST /api/openlineage`，只做 ingest 编排。
- [x] 实现 `OpenLineageService#createAsync(...)`：并行执行 MySQL 原始事件写入 + Neo4j 资产写入。
- [x] 实现 `OpenLineageEventDao`：写 `lineage_events`（强制写入 `tenant_id/tenant_code/tenant_name`）。
- [x] 实现 `OpenLineageGraphDao`：按 tenant 作用域 upsert 节点/关系，禁止跨租户连边。
- [x] 在主链路成功后 enqueue 异步任务到 `ol_async_task`（`EMBEDDING/SQL_SUMMARY`）。
- [x] enqueue 成功后立即 push `taskId` 到 Worker 执行通道（主执行路径）。

### 10.3 异步能力链路（Async Capability -> Task Tracking(update)）

- [x] 实现 `SqlSummaryWorker`：按 `tenant + summaryModel` 微批执行摘要并回写 SQL 节点。
- [x] 实现 `EmbeddingTaskWorker`：按 `tenant + embeddingModel` 微批执行向量化并回写资产业务字段。
- [x] 实现任务状态更新：`RUNNING -> SUCCEEDED/FAILED/DEAD`，写 `ol_async_task_attempt` 与 `ol_async_batch`。
- [x] 实现失败重试与退避：`max_retry`、`next_run_at`、错误分类落库。

### 10.4 恢复补偿链路（仅异常恢复触发）

- [x] 实现恢复消费者：仅在重启/崩溃/超时场景下，从 `ol_async_task` 拉取 `PENDING/FAILED`。
- [x] 实现并发认领：`FOR UPDATE SKIP LOCKED` + `claim_token/claim_until` 防重复执行。
- [x] 验证恢复链路不影响主链路语义：主链路成功不依赖异步任务完成。

### 10.5 验收与发布门禁

- [x] 单元测试：`TenantResolver`、`OpenLineageService#createAsync`、`OpenLineageDao`、Worker 重试逻辑。
- [x] 集成测试：计算引擎事件（无 facet tenant）与 Gravitino 事件（有 facet tenant）全链路校验。
- [x] 压测验证：主链路吞吐、异步积压、`DEAD` 比例、租户公平性。
- [x] 观测接入：主链路与异步链路指标、错误码、任务状态面板可见。
- [x] 发布验收：灰度租户验证通过后，生产启用实时接入。
