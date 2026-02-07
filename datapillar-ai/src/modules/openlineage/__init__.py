# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage Sink 模块

企业级 OpenLineage 事件接收和处理服务

功能：
- 接收 OpenLineage 事件（Flink/Spark/Gravitino/Hive）
- 限流保护
- 事件过滤
- 异步队列处理
- 批量写入 Neo4j
- 重试机制
- 监控统计

API 端点：
- POST /api/ai/openlineage - 接收事件
- GET /api/ai/openlineage/stats - 统计信息

配置示例（Gravitino openlineage.conf）：
```
gravitino.openlineage.transport.url = http://datapillar-ai:7003
gravitino.openlineage.transport.endpoint = /api/ai/openlineage
```
"""

from src.modules.openlineage.api import router
from src.modules.openlineage.core.embedding_processor import (
    EmbeddingProcessor,
    get_embedding_processor,
)
from src.modules.openlineage.core.event_processor import EventProcessor, get_event_processor
from src.modules.openlineage.schemas.events import (
    Dataset,
    EventType,
    InputDataset,
    Job,
    OutputDataset,
    Run,
    RunEvent,
)

__all__ = [
    "router",
    "EventProcessor",
    "get_event_processor",
    "EmbeddingProcessor",
    "get_embedding_processor",
    "Dataset",
    "EventType",
    "InputDataset",
    "Job",
    "OutputDataset",
    "Run",
    "RunEvent",
]
