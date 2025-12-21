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
- POST /api/v1/lineage - 接收事件
- GET /api/v1/lineage/stats - 统计信息
- GET /api/v1/lineage/health - 健康检查
- POST /api/v1/lineage/start - 启动服务
- POST /api/v1/lineage/stop - 停止服务

配置示例（各系统 OpenLineage 配置）：
```yaml
transport:
  type: http
  url: http://datapillar-ai:8000/api/v1/lineage
```
"""

from src.modules.openlineage.api import router
from src.modules.openlineage.config import OpenLineageSinkConfig
from src.modules.openlineage.schemas.events import (
    Dataset,
    EventType,
    InputDataset,
    Job,
    OutputDataset,
    Run,
    RunEvent,
)
from src.modules.openlineage.service import OpenLineageSinkService, SinkStats

__all__ = [
    "router",
    "OpenLineageSinkConfig",
    "OpenLineageSinkService",
    "SinkStats",
    "Dataset",
    "EventType",
    "InputDataset",
    "Job",
    "OutputDataset",
    "Run",
    "RunEvent",
]
