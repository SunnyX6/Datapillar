"""
上下文压缩模块

两大压缩类型：
- artifact.py: 产物索引（Analysis/Plan/SQL 的元数据）
- requirement.py: 需求压缩（用户对话 → TODO 清单）

辅助模块：
- budget.py: 预算判断（何时需要压缩）
- clip.py: 载荷裁剪（截断过长字符串/列表）
"""

# 产物索引
from src.modules.etl.context.compress.artifact import (
    ArtifactStatus,
    ArtifactStore,
    ArtifactType,
)

# 预算判断
from src.modules.etl.context.compress.budget import (
    CompressionReason,
    CompressionScope,
    ContextBudget,
)

# 载荷裁剪
from src.modules.etl.context.compress.clip import clip_payload

# 需求压缩
from src.modules.etl.context.compress.requirement import (
    RequirementTodoDelta,
    RequirementTodoSnapshot,
    RequirementTodoStore,
)

__all__ = [
    # 产物索引
    "ArtifactStore",
    "ArtifactType",
    "ArtifactStatus",
    # 需求压缩
    "RequirementTodoStore",
    "RequirementTodoSnapshot",
    "RequirementTodoDelta",
    # 预算判断
    "ContextBudget",
    "CompressionScope",
    "CompressionReason",
    # 载荷裁剪
    "clip_payload",
]
