# @author Sunny
# @date 2026-01-27

"""
Knowledge graph data access layer(Knowledge Repository)

Positioning:- This is Neo4j on"Knowledge graph/Digital warehouse assets"unified data access layer
- supply ETL,Multi-agent and governance capabilities are reused together

Module division:- search_table:Table query(Contains columns,range,Bloodline)
- search_column:column query(reserved)
- search_metric:Indicator query
- search_semantic:Semantic asset query(root,modifier,unit)
- search_node:Node search(Hybrid search)
- search_sql:SQL Search
- sync_metadata:Synchronous metadata writing
- sync_lineage:Synchronous blood relationship writing
- writeback:writeback operation
- dto:data transfer object

border:- Only data access is done here(Cypher Query/Search/write back),Does not carry business orchestration
"""

from src.infrastructure.repository.knowledge.dto import (
    MetricDTO,
    ModifierDTO,
    UnitDTO,
    WordRootDTO,
)
from src.infrastructure.repository.knowledge.search_column import Neo4jColumnSearch
from src.infrastructure.repository.knowledge.search_metric import Neo4jMetricSearch
from src.infrastructure.repository.knowledge.search_node import Neo4jNodeSearch, SearchHit
from src.infrastructure.repository.knowledge.search_semantic import Neo4jSemanticSearch
from src.infrastructure.repository.knowledge.search_sql import Neo4jSQLSearch, SQLHit
from src.infrastructure.repository.knowledge.search_table import Neo4jTableSearch
from src.infrastructure.repository.knowledge.sync_lineage import Lineage
from src.infrastructure.repository.knowledge.sync_metadata import Metadata, TableUpsertPayload
from src.infrastructure.repository.knowledge.writeback import Neo4jKGWritebackRepository

__all__ = [  # Search service
    "Neo4jTableSearch",
    "Neo4jColumnSearch",
    "Neo4jMetricSearch",
    "Neo4jSemanticSearch",
    "Neo4jNodeSearch",
    "Neo4jSQLSearch",  # metadata/Bloodline
    "Metadata",
    "TableUpsertPayload",
    "Lineage",  # write back service
    "Neo4jKGWritebackRepository",  # data transfer object
    "SearchHit",
    "SQLHit",
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
    "MetricDTO",
]
