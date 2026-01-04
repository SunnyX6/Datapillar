"""
ETL 多智能体共享状态

只有一个状态：Blackboard（Boss的工作台）

设计原则：
- Blackboard 只放老板关心的信息（任务、汇报、交付物）
- 不放员工内部逻辑（对话历史、压缩策略、中间计算）
- 员工交接物和私有存储在 context/ 目录
"""

from src.modules.etl.state.blackboard import (
    AgentReport,
    Blackboard,
    ReportStatus,
)

__all__ = [
    "AgentReport",
    "Blackboard",
    "ReportStatus",
]
