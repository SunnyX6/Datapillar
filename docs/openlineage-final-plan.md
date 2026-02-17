# Datapillar OpenLineage 最终方案（Java / 单进程 / 无 Kafka）

## 1. 目标与范围
- **服务名**：`datapillar-openlineage`
- **职责**：只处理 OpenLineage 事件的接收、解析、落库与向量化
- **不包含**：知识图谱查询、RAG 查询、向量重建接口、Python 服务
- **端点前缀**：统一放在 `/api/openlineage`（不使用 `/ai`）

## 2. 约束（强制）
- 只用 **Java** 实现，不引入 Python 进程
- **单进程**、单 Spring Boot 启动类
- **无 Kafka**（现阶段）
- **只保留两个线程池**：HTTP 线程池 + 消费线程池
- **只用内存队列**：`eventQueue` 与 `embeddingQueue`
- 向量化为 **异步**，事件落库优先

## 3. 代码目录结构（Java 规范）
对齐现有 Java 服务的包约定（如 `datapillar-auth` / `datapillar-studio-service` / `datapillar-api-gateway`），统一 `com.sunny.datapillar.<service>` 根包，按领域拆分：

```
datapillar-openlineage/
├─ pom.xml
└─ src/
   ├─ main/
   │  ├─ java/
   │  │  └─ com/sunny/datapillar/openlineage/
   │  │     ├─ OpenLineageApplication.java
   │  │     ├─ config/                 # 线程池、队列、Neo4j、OpenLineage 配置
   │  │     ├─ controller/             # HTTP 入口（/api/openlineage）
   │  │     ├─ module/
   │  │     │  ├─ ingest/              # RunEvent 校验与解析入口
   │  │     │  ├─ queue/               # eventQueue / embeddingQueue / stats
   │  │     │  ├─ processor/           # EventProcessor / EmbeddingProcessor
   │  │     │  ├─ writer/              # Neo4j 写入（节点/边/SQL）
   │  │     │  └─ embedding/           # EmbeddingTask / ModelResolver / Client
   │  │     ├─ repository/
   │  │     │  ├─ neo4j/               # Neo4j 读写封装
   │  │     │  └─ mysql/               # ai_model 查询
   │  │     └─ util/
   │  └─ resources/
   │     ├─ application.yml
   │     └─ neo4j/init.cypher
   └─ test/java/com/sunny/datapillar/openlineage/
```

> 说明：不复刻 Python 目录结构，严格遵循现有 Java 服务习惯。

## 4. 接口定义
- `POST /api/openlineage`
  - 入参：OpenLineage `RunEvent`
  - 行为：校验 -> 入队 -> 返回 `202 Accepted`
- `GET /api/openlineage/stats`
  - 输出：队列长度、消费速率、失败计数

> 说明：本服务不提供知识图谱查询，不提供向量重建接口。

## 5. 架构概览（单进程）
```
HTTP (Spring MVC)
   -> eventQueue（内存有界队列）
   -> Consumer Pool
       -> 解析事件
       -> 写入 Neo4j（节点、边、SQL 节点）
       -> 生成 SQL embedding 任务 -> embeddingQueue
       -> 批量向量化 -> 写入 Neo4j
```

## 6. 线程与队列设计
- **线程池**：
  - HTTP 线程池（Tomcat 或 Spring 默认）
  - 消费线程池（自建，处理 event 与 embedding）
- **队列**：
  - `eventQueue`：存放 OpenLineage 事件
  - `embeddingQueue`：存放待向量化任务
- **背压**：
  - 队列满时，`POST /api/openlineage` 直接返回 `503`
- **调度策略**：
  - 消费线程池轮询 `eventQueue` 与 `embeddingQueue`，避免饥饿

## 7. 数据流（最终一致）
1. HTTP 接收 OpenLineage 事件
2. 写入 `eventQueue`
3. 消费线程解析事件
4. **优先落库 Neo4j**（节点、边、SQL 节点）
5. 生成 SQL embedding 任务
6. 进入 `embeddingQueue`
7. 批量向量化并写回 Neo4j

- SQL 节点 **也需要向量**
- 向量为 **异步最终一致**

## 8. 模型配置（从数据库读取）
- 不使用 `embedding.provider`、`embedding.model`、`embedding.base_url`、`embedding.dimension` 静态配置
- 每次向量化从 `ai_model` 表读取 **租户默认模型**

### ai_model 表设计（创建时即包含约束）
> 仅租户维度，不使用 `created_by`

**DDL 示例（MySQL）**：
```sql
CREATE TABLE ai_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  model_type VARCHAR(32) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  base_url VARCHAR(255) NOT NULL,
  dimension INT NOT NULL,
  -- is_default 只用 0/1
  is_default TINYINT NOT NULL DEFAULT 0
);
```

## 9. 租户隔离
- OpenLineage 事件必须携带 `tenant_id`
- 所有写入 Neo4j 的节点与边必须带 `tenant_id`
- 查询默认模型时必须 `WHERE tenant_id = ?`

## 10. 失败处理与一致性
- **事件落库失败**：重试（有限次）并记录失败计数
- **向量化失败**：重试（有限次）；失败不会阻塞事件落库
- **一致性**：向量为最终一致；服务重启会丢失内存队列

## 11. 可观测性
- 监控指标：
  - `eventQueue` / `embeddingQueue` 长度
  - 处理速率（events/sec，embeddings/sec）
  - 失败计数

## 12. 配置项（最小集）
- `server.servlet.context-path=/api/openlineage`
- `eventQueue.capacity`
- `embeddingQueue.capacity`
- `consumer.pool.size`
- `embedding.batch.size`
- `embedding.flush.interval.ms`

## 13. 非目标
- 不做 Kafka
- 不做多进程或多服务拆分
- 不提供知识图谱查询或 RAG 接口
- 不提供向量重建接口
