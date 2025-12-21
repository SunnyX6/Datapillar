"""
SQL Facet 解析器

从 sql facet 解析出 SQL 节点
"""

import hashlib

from src.modules.openlineage.parsers.base import BaseFacetParser
from src.modules.openlineage.schemas.events import RunEvent
from src.modules.openlineage.schemas.neo4j import SQLNode


class SQLFacetParser(BaseFacetParser[SQLNode]):
    """
    SQL Facet 解析器

    从 job.facets.sql 中解析出 SQL 节点
    """

    @property
    def facet_name(self) -> str:
        return "sql"

    def can_parse(self, event: RunEvent) -> bool:
        """检查事件是否包含 sql facet"""
        return event.has_sql_facet()

    def parse(self, event: RunEvent) -> list[SQLNode]:
        """解析 sql facet"""
        sql = event.get_sql()
        if not sql:
            return []

        sql_id = self._generate_sql_id(sql, event)
        dialect = event.get_sql_dialect()
        engine = event.get_producer_type()

        sql_node = SQLNode(
            id=sql_id,
            content=sql,
            dialect=dialect,
            engine=engine,
            job_namespace=event.job.namespace,
            job_name=event.job.name,
        )

        return [sql_node]

    def _generate_sql_id(self, sql: str, event: RunEvent) -> str:
        """
        生成 SQL 节点 ID

        基于 job namespace + job name + normalized SQL 生成唯一 ID
        """
        normalized_sql = " ".join(sql.lower().split())
        content = f"{event.job.namespace}:{event.job.name}:{normalized_sql}"
        return hashlib.sha256(content.encode()).hexdigest()[:16]
