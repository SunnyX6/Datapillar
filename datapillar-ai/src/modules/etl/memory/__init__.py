"""
ETL 记忆模块（session 隔离）

记忆分层（新架构）：
- 长期记忆：Neo4j 知识图谱（数据资产、血缘关系）
- 短期记忆：SessionMemory（需求TODO、Agent状态、对话摘要）~3-4KB，通过 Checkpointer 持久化
- 运行时：Handover（交接物原文，用完即弃，不持久化）

设计原则：
- 压缩后再记忆（compress → memorize）
- Blackboard 只放老板关心的信息
- 大块数据（SQL原文、Workflow JSON）不存记忆，放 Handover
"""

from src.modules.etl.memory.session_checkpoint import clear_session_checkpoints
from src.modules.etl.memory.session_memory import SessionMemory

__all__ = [
    "SessionMemory",
    "clear_session_checkpoints",
]
