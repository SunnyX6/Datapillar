# OpenLineage Listener for Gravitino

将 Gravitino 元数据事件转换为标准 OpenLineage RunEvent，通过 OpenLineage Client 发送。

## 架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Gravitino Server                              │
│                              ↓                                       │
│   CreateTableEvent / AlterTableEvent / DropTableEvent / ...         │
│                              ↓                                       │
│              OpenLineageGravitinoListener                           │
│                              ↓                                       │
│              GravitinoEventConverter                                │
│                              ↓                                       │
│              OpenLineage RunEvent (标准格式)                         │
│                              ↓                                       │
│              OpenLineage Client                                     │
│                              ↓                                       │
│              Transport (neo4j / kafka / http / console)             │
└─────────────────────────────────────────────────────────────────────┘
```

## 事件映射

| Gravitino 事件 | OpenLineage 事件 |
|----------------|------------------|
| CreateTableEvent | RunEvent (COMPLETE) + OutputDataset (CREATE) |
| AlterTableEvent | RunEvent (COMPLETE) + OutputDataset (ALTER) |
| DropTableEvent | RunEvent (COMPLETE) + OutputDataset (DROP) |
| CreateSchemaEvent | RunEvent (COMPLETE) + OutputDataset (CREATE) |
| DropSchemaEvent | RunEvent (COMPLETE) + OutputDataset (DROP) |
| CreateCatalogEvent | RunEvent (COMPLETE) + OutputDataset (CREATE) |
| DropCatalogEvent | RunEvent (COMPLETE) + OutputDataset (DROP) |

## 配置

```properties
# gravitino.conf

# 启用 OpenLineage Listener
gravitino.eventListener.names = openlineage
gravitino.eventListener.openlineage.class = org.apache.gravitino.listener.openlineage.OpenLineageGravitinoListener

# 命名空间
gravitino.eventListener.openlineage.namespace = gravitino

# Transport 配置 (与 Spark/Flink OpenLineage 集成相同)
gravitino.eventListener.openlineage.transport.type = neo4j
gravitino.eventListener.openlineage.transport.uri = bolt://localhost:7687
gravitino.eventListener.openlineage.transport.username = neo4j
gravitino.eventListener.openlineage.transport.password = password
```

## Transport 类型

| 类型 | 说明 | 配置示例 |
|------|------|----------|
| console | 输出到日志 | `transport.type=console` |
| http | 发送到 HTTP 端点 | `transport.type=http`, `transport.url=http://marquez:5000` |
| kafka | 发送到 Kafka | `transport.type=kafka`, `transport.topicName=openlineage.events` |
| neo4j | 写入 Neo4j | `transport.type=neo4j`, `transport.uri=bolt://neo4j:7687` |

## 构建

```bash
cd datapillar-gravitino
./gradlew :openlineage-listener:build
```

## 部署

构建后的 JAR 会自动包含在 Gravitino 发行版中。

确保 `openlineage-transport-neo4j` JAR 也在 classpath 中（如果使用 Neo4j transport）。

## 与 Spark/Flink 统一

本 Listener 发出的事件格式与 OpenLineage 官方 Spark/Flink 集成完全一致：

```
Spark  → OpenLineage Spark Listener  → OpenLineage Client → Transport → Neo4j
Flink  → OpenLineage Flink Listener  → OpenLineage Client → Transport → Neo4j
Gravitino → OpenLineageGravitinoListener → OpenLineage Client → Transport → Neo4j
```

所有事件都是标准 OpenLineage RunEvent 格式，存储在同一个 Neo4j 实例中，可以统一查询。
