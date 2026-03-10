对，刚才那版我给大了，**错了**。

你说得对，这里**不该改 `source` 这个抽象名，也不该去动 `model` 包**。
正确修复应该是：

- **保留 `source`**
- **保留现有 `model`**
- **只把职责搬正**
- **不做那种自嗨式重构**

**正确方案**

**1. `source` 不改名，但职责要变真**
- 现有这些类保留：
  - `GravitinoOpenlineageSource`
  - `HiveSource`
  - `FlinkSource`
  - `SparkSource`
- 它们不再只是把 JSON 包成 `OpenLineageEvent`
- 它们要真正负责：
  - 判断自己能不能处理这个 MQ payload
  - 解析事件语义
  - 解析 `family`
  - 解析资源名、dataset、facet
  - **产出现有 `model` 对象**

也就是说，`source` 这个抽象保留，但它要**真的像 source adapter 一样工作**，而不是现在这种空壳。

**2. `model` 包不动**
- 保留现有模型：
  - `Catalog`
  - `Schema`
  - `Table`
  - `Column`
  - `Metric`
  - `MetricVersion`
  - `Tag`
  - `TagRelation`
  - `WordRoot`
  - `Modifier`
  - `Unit`
  - `ValueDomain`
  - `Tenant`
- 不改类名
- 不重新造一套“新领域模型”
- `source` 解析后，直接产出这些对象

**3. `pipeline` 只做编排，不做业务解析**
- `EventPipeline` 应该只做这几件事：
  - 消费 MQ
  - 重试 / DLQ
  - 选中哪个 `source`
  - 调 `source` 解析出当前 `model`
  - 解析 tenant
  - 调 `sink`
  - 再发 embedding task

也就是说，`EventPipeline` 不该再干这些破事：
- 不该自己 `resolveFamily`
- 不该自己判断 catalog/schema/table/metric/tag
- 不该自己理解 OpenLineage 业务语义

当前这些逻辑都应该从 `EventPipeline` 拿掉：
- `resolveFamily(...)`
- `isGravitinoPayload(...)`
- `isEnginePayload(...)`
- `hasFacet(...)`
- `collectDatasets(...)`

**4. `GraphDao` 不能再解析事件**
现在最脏的点就在这里：
- `GraphDao.applyRealtimeEvent(...)` 直接吃 `OpenLineageEvent + family`
- 然后在 DAO 里按 `family` 分支
- 再从 raw event 里抽资源名、抽 facet、抽 dataset
- 这他妈根本不是 DAO

要改成：
- `GraphDao` 只做**落库**
- 不再接受 `OpenLineageEvent`
- 不再接受 `JsonNode`
- 不再接受 `family`
- 不再做 `extractResourceName / extractFacetValue / resolveDatasetRefs`

这些垃圾都应该从 `GraphDao` 拿走：
- `handleCatalogEvent(...)`
- `handleSchemaEvent(...)`
- `handleTableEvent(...)`
- `handleMetricEvent(...)`
- `handleTagEvent(...)`
- `handleSimpleNodeEvent(...)`
- `extractResourceName(...)`
- `extractFacetValue(...)`
- `resolveDatasetRefs(...)`
- `toDatasetRef(...)`

这些逻辑应该回到各个 `source` 里。

**5. `sink` 才负责把 model 写进 dao**
- `GraphSink` 现在太薄了，基本只是转发
- 正确做法是：
  - `source` 解析出当前 `model`
  - `pipeline` 把这些 `model` 交给 `GraphSink`
  - `GraphSink` 根据模型类型调 `GraphDao`
- `GraphDao` / `VectorDao` 必须放到 `sink/dao` 目录下
- **不建子目录，直接平铺**
- `dao` 只是 `sink` 的落地细节，不再作为独立解析层存在
- `GraphDao` 只保留纯写入方法，例如：
  - `upsertCatalog(...)`
  - `upsertSchema(...)`
  - `upsertTable(...)`
  - `upsertColumn(...)`
  - `upsertMetric(...)`
  - `upsertTag(...)`
  - `upsertWordRoot(...)`
  - `upsertModifier(...)`
  - `upsertUnit(...)`
  - `upsertValueDomain(...)`
  - `linkSchemaToCatalog(...)`
  - `linkTableToSchema(...)`
  - `linkColumnToTable(...)`
  - `linkMetricToSchema(...)`
  - `linkTagToTarget(...)`

**6. `OpenLineageEvent` 可以保留，但只能作为 source 内部中间态**
- 我收回前面“直接删掉”的说法
- 你说得对，不需要上来就砍抽象
- 如果要保留 `OpenLineageEvent`，也只能这样用：
  - 在 `source` 内部做第一层协议解析
  - 然后立刻转成现有 `model`
- **不能再让它流到 `pipeline -> sink -> dao`**
- 也就是说，`OpenLineageEvent` 最多是 `source` 内部临时对象，不该成为后续主干入参

**7. 实时链路正确长相**
- `OpenLineage Event`
- `-> MQ`
- `-> EventPipeline`
- `-> 选择某个 Source`
- `-> Source 解析出当前 model`
- `-> GraphSink`
- `-> GraphDao`
- `-> Neo4j`
- `-> EmbeddingEventPublisher`
- `-> Embedding MQ`
- `-> EmbeddingPipeline`
- `-> VectorSink`
- `-> VectorDao`

这条路里：
- `source` 负责解析
- `pipeline` 负责编排
- `sink` 负责输出
- `dao` 负责落地

这才是对的。

**8. embedding 继续保持单 topic / 单消息模型**
- 不再搞额外 runtime cache / projection
- 发布 embedding 消息时，**直接把运行时模型快照带上**：
  - `aiModelId`
  - `providerCode`
  - `providerModelId`
  - `embeddingDimension`
  - `baseUrl`
  - `apiKeyCiphertext`
  - `targetRevision`
- embedding 消费时不再回头查一遍“当前运行时模型”
- 也不再额外拆第二套 topic / 第二套数据模型
- 统一走一套 embedding task 消息结构

**9. rebuild 链路保持一致**
- `RebuildCommandPipeline` 现在已经是：
  - 从 `GravitinoDBSource` 拉元数据
  - 得到现有 `model` 列表
  - 再交给 `GraphSink`
- 所以 realtime 也应该和 rebuild 对齐
- 也就是说，**realtime source 解析出来的终态，也应该是这批现有 `model`**
- 这样两条链最后都走：
  - `model -> GraphSink -> GraphDao`

这才统一，不会一条链吃 `OpenLineageEvent`，另一条链吃 `Catalog/Table/Metric`

**10. 最小手术式修复顺序**
- 第一步：保留 `source` 类名，给它们补真正解析逻辑
- 第二步：把 `EventPipeline` 里的 `family` 判断和 source 识别逻辑下沉到 `source`
- 第三步：把 `GraphDao` 里的事件语义解析逻辑挖出来，放回 `source`
- 第四步：把 `GraphDao` 改成纯写入 DAO
- 第五步：让 `GraphSink` 真正接收现有 `model` 并调 DAO
- 第六步：让 `EventPipeline` 只做编排
- 第七步：保持 embedding 这条单 topic / 单消息模型不变

**一句话定版**
- **不改 `source` 抽象名**
- **不改现有 `model`**
- **只把“事件解析”搬进 `source`**
- **把“业务编排”留在 `pipeline`**
- **把“写图输出”留在 `sink`**
- **把 `dao` 平铺压到 `sink/dao` 目录里**
- **把“持久化细节”压回 `dao`**
- **embedding 只维护一套 topic / 一套消息模型**

这才是你要的那种结构。
