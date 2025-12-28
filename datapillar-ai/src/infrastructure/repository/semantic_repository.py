"""
语义元数据仓库（Semantic Repository）
负责从 Neo4j 知识图谱查询词根、修饰符、单位等语义元数据
"""

import logging
from dataclasses import dataclass
from typing import Any

from src.infrastructure.database import AsyncNeo4jClient
from src.shared.config import settings

logger = logging.getLogger(__name__)


@dataclass
class WordRootDTO:
    """词根"""

    code: str
    name: str | None = None
    data_type: str | None = None
    description: str | None = None


@dataclass
class ModifierDTO:
    """修饰符"""

    code: str
    modifier_type: str | None = None
    description: str | None = None


@dataclass
class UnitDTO:
    """单位"""

    code: str
    name: str | None = None
    symbol: str | None = None
    description: str | None = None


@dataclass
class TableContextDTO:
    """表上下文"""

    catalog: str
    schema: str
    table: str
    description: str | None = None
    columns: list[dict[str, Any]] | None = None


@dataclass
class MetricDTO:
    """指标（精简版，用于 AI 上下文）"""

    code: str
    name: str | None = None
    description: str | None = None


class SemanticRepository:
    """语义元数据仓库（Neo4j）"""

    @classmethod
    async def get_word_roots(cls, limit: int = 100) -> list[WordRootDTO]:
        """
        获取词根列表

        Args:
            limit: 最大返回数量

        Returns:
            词根列表
        """
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (w:WordRoot)
        RETURN w.code AS code, w.name AS name, w.dataType AS dataType, w.description AS description
        ORDER BY w.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个词根")

        return [
            WordRootDTO(
                code=r["code"],
                name=r["name"],
                data_type=r["dataType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_modifiers(cls, limit: int = 100) -> list[ModifierDTO]:
        """
        获取修饰符列表

        Args:
            limit: 最大返回数量

        Returns:
            修饰符列表
        """
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (m:Modifier)
        RETURN m.code AS code, m.modifierType AS modifierType, m.description AS description
        ORDER BY m.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个修饰符")

        return [
            ModifierDTO(
                code=r["code"],
                modifier_type=r["modifierType"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_units(cls, limit: int = 100) -> list[UnitDTO]:
        """
        获取单位列表

        Args:
            limit: 最大返回数量

        Returns:
            单位列表
        """
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (u:Unit)
        RETURN u.code AS code, u.name AS name, u.symbol AS symbol, u.description AS description
        ORDER BY u.code
        LIMIT $limit
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(query, limit=limit)
            records = await result.data()

        logger.debug(f"从 Neo4j 获取 {len(records)} 个单位")

        return [
            UnitDTO(
                code=r["code"],
                name=r["name"],
                symbol=r["symbol"],
                description=r["description"],
            )
            for r in records
        ]

    @classmethod
    async def get_table_context(
        cls, catalog: str, schema: str, table: str
    ) -> TableContextDTO | None:
        """
        获取表的上下文信息（描述、列信息、值域等）

        Args:
            catalog: 数据源名称
            schema: 数据库名称
            table: 表名

        Returns:
            表上下文，包含描述、列信息和值域
        """
        driver = await AsyncNeo4jClient.get_driver()

        query = """
        MATCH (cat:Catalog {name: $catalog})-[:HAS_SCHEMA]->(sch:Schema {name: $schema})-[:HAS_TABLE]->(t:Table {name: $table})
        OPTIONAL MATCH (t)-[:HAS_COLUMN]->(col:Column)
        OPTIONAL MATCH (col)-[:HAS_VALUE_DOMAIN]->(vd:ValueDomain)
        WITH t, col, vd
        ORDER BY col.name
        WITH t, collect({
            name: col.name,
            dataType: col.dataType,
            description: col.description,
            valueDomain: CASE WHEN vd IS NOT NULL THEN {
                code: vd.domainCode,
                name: vd.domainName,
                type: vd.domainType,
                items: vd.items
            } ELSE null END
        }) AS columns
        RETURN
            t.name AS name,
            t.description AS description,
            columns
        """

        async with driver.session(database=settings.neo4j_database) as session:
            result = await session.run(
                query, catalog=catalog, schema=schema, table=table
            )
            record = await result.single()

        if not record:
            logger.debug(f"未找到表: {catalog}.{schema}.{table}")
            return None

        logger.debug(f"从 Neo4j 获取表上下文: {catalog}.{schema}.{table}")

        return TableContextDTO(
            catalog=catalog,
            schema=schema,
            table=record["name"],
            description=record["description"],
            columns=record["columns"],
        )
