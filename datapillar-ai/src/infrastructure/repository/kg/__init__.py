# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识图谱数据访问层（KG Repository）

定位：
- 这是 Neo4j 上"知识图谱/数仓资产"的统一数据访问层
- 供前端知识图谱查询（src/modules/knowledge）与 ETL 多智能体工具（src/modules/etl/tools）共同复用

模块划分：
- search_table: 表查询（含列、值域、血缘）
- search_column: 列查询（预留）
- search_metric: 指标查询
- search_semantic: 语义资产查询（词根、修饰符、单位）
- search_node: 节点搜索（混合检索）
- search_sql: SQL 搜索
- writeback: 写回操作
- dto: 数据传输对象

边界：
- 这里只做数据访问（Cypher 查询/检索/写回），不承载业务编排
"""

from src.infrastructure.repository.kg.dto import (
    MetricDTO,
    ModifierDTO,
    UnitDTO,
    WordRootDTO,
)
from src.infrastructure.repository.kg.search_column import Neo4jColumnSearch
from src.infrastructure.repository.kg.search_graph import Neo4jGraphSearch
from src.infrastructure.repository.kg.search_metric import Neo4jMetricSearch
from src.infrastructure.repository.kg.search_node import Neo4jNodeSearch, SearchHit
from src.infrastructure.repository.kg.search_semantic import Neo4jSemanticSearch
from src.infrastructure.repository.kg.search_sql import Neo4jSQLSearch, SQLHit
from src.infrastructure.repository.kg.search_table import Neo4jTableSearch
from src.infrastructure.repository.kg.writeback import Neo4jKGWritebackRepository

__all__ = [
    # 搜索服务
    "Neo4jTableSearch",
    "Neo4jColumnSearch",
    "Neo4jMetricSearch",
    "Neo4jSemanticSearch",
    "Neo4jNodeSearch",
    "Neo4jSQLSearch",
    "Neo4jGraphSearch",
    # 写回服务
    "Neo4jKGWritebackRepository",
    # 数据传输对象
    "SearchHit",
    "SQLHit",
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
    "MetricDTO",
]
