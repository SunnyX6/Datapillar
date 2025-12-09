"""
数据访问层（Repository Layer）

职责：
- 封装所有数据库查询逻辑
- 提供统一的数据访问接口
- 将数据库操作从业务逻辑中分离

架构：
├── repositories/
│   ├── __init__.py
│   ├── model_repository.py      # AI 模型数据访问（MySQL）
│   └── knowledge_repository.py  # 知识图谱数据访问（Neo4j）
"""

from src.repositories.model_repository import ModelRepository
from src.repositories.knowledge_repository import KnowledgeRepository
from src.repositories.component_repository import ComponentRepository

__all__ = [
    "ModelRepository",
    "KnowledgeRepository",
    "ComponentRepository",
]
