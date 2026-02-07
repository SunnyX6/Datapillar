# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识图谱服务层

提供:
- 初始图数据加载
- 自然语言搜索 (向量/全文/混合)
- Gravitino 元数据同步（按需、可重复的全量或范围同步）
"""

import json
import logging
import time
from collections.abc import AsyncGenerator
from dataclasses import dataclass
from typing import Any, Awaitable, Callable

logger = logging.getLogger(__name__)

from neo4j import AsyncSession

from src.infrastructure.database.gravitino import GravitinoDBClient
from src.infrastructure.repository.knowledge import Neo4jGraphSearch, Neo4jNodeSearch
from src.infrastructure.repository.knowledge.dto import (
    ModifierDTO,
    UnitDTO,
    ValueDomainDTO,
    WordRootDTO,
    generate_id,
)
from src.infrastructure.repository.neo4j_uow import neo4j_async_session
from src.infrastructure.repository.knowledge import Lineage, Metadata, TableUpsertPayload
from src.modules.knowledge.schemas import (
    GraphData,
    GraphNode,
    GraphRelationship,
    KGEventType,
    KGStreamEvent,
    get_node_level,
)
from src.modules.knowledge.utils import msgpack_encode
from src.modules.knowledge.embedding_processor import get_embedding_processor
from src.shared.config import settings
from src.shared.config.runtime import get_default_tenant_id

BATCH_SIZE = 200
SYNC_STEP = "sync"
EMBEDDING_STEP = "embedding"

LEVEL_ALL = "all"
LEVEL_CATALOG = "catalog"
LEVEL_SCHEMA = "schema"
LEVEL_TABLE = "table"


class KnowledgeGraphService:
    """知识图谱服务"""

    def get_initial_graph(self, limit: int = 50) -> GraphData:
        """
        获取初始图数据 (全图)

        Args:
            limit: 节点数量限制

        Returns:
            GraphData: 包含节点和关系的图数据
        """
        try:
            records = Neo4jGraphSearch.get_initial_graph(limit)
            if not records:
                return GraphData()

            record = records[0]
            nodes = []
            for n in record.get("nodes") or []:
                if n:
                    node_type = n.get("type", "")
                    nodes.append(
                        GraphNode(
                            id=n["id"],
                            type=node_type,
                            level=get_node_level(node_type),
                            properties=n.get("properties", {}),
                        )
                    )

            relationships = [
                GraphRelationship(**r) for r in (record.get("relationships") or []) if r
            ]

            logger.info(f"加载初始图数据: {len(nodes)} 节点, {len(relationships)} 关系")
            return GraphData(nodes=nodes, relationships=relationships)

        except Exception as e:
            logger.error(f"加载初始图数据失败: {e}")
            raise

    def search_by_text(
        self,
        query: str,
        top_k: int = 10,
        search_type: str = "hybrid",
        tenant_id: int | None = None,
    ) -> dict[str, Any]:
        """
        自然语言搜索

        Args:
            query: 搜索文本
            top_k: 返回数量
            search_type: 搜索类型 (vector/fulltext/hybrid)

        Returns:
            搜索结果（包含节点、关系、高亮节点ID）
        """
        logger.info(f"搜索知识图谱: query='{query}', top_k={top_k}, type={search_type}")

        try:
            # 通过搜索服务执行检索
            if search_type == "vector":
                results = Neo4jNodeSearch.vector_search(
                    query,
                    top_k,
                    vector_index="kg_unified_vector_index",
                    tenant_id=tenant_id,
                )
            elif search_type == "hybrid":
                results = Neo4jNodeSearch.hybrid_search(query, top_k, tenant_id=tenant_id)
            else:
                raise ValueError(f"不支持的搜索类型: {search_type}，仅支持 vector/hybrid")

            # 直接从检索结果提取 node_id（SearchHit 对象）
            matched_node_ids = [hit.node_id for hit in results if hit.node_id]

            # 扩展图数据
            expanded_result = self._expand_graph_data(matched_node_ids)

            logger.info(f"搜索完成: 找到 {len(expanded_result['nodes'])} 个节点")

            return {
                "nodes": expanded_result["nodes"],
                "relationships": expanded_result["relationships"],
                "highlight_node_ids": matched_node_ids,
            }

        except Exception as e:
            logger.error(f"搜索失败: {e}", exc_info=True)
            return {"nodes": [], "relationships": [], "highlight_node_ids": []}

    def _expand_graph_data(self, node_ids: list[str]) -> dict[str, list]:
        """扩展图数据：获取匹配节点及其相关关系"""
        if not node_ids:
            return {"nodes": [], "relationships": []}

        data = Neo4jGraphSearch.get_graph(node_ids)

        nodes = [
            GraphNode(
                id=n["id"],
                type=n.get("type", ""),
                level=get_node_level(n.get("type", "")),
                properties=n.get("properties", {}),
            ).model_dump()
            for n in (data.get("nodes") or [])
            if n
        ]

        relationships = [
            GraphRelationship(**r).model_dump() for r in (data.get("relationships") or []) if r
        ]

        return {"nodes": nodes, "relationships": relationships}

    async def stream_initial_graph(self, limit: int = 500) -> AsyncGenerator[KGStreamEvent, None]:
        """流式获取初始图数据"""
        try:
            graph_data = self.get_initial_graph(limit=limit)
            nodes = [n.model_dump() for n in graph_data.nodes]
            rels = [r.model_dump() for r in graph_data.relationships]
            total_nodes = len(nodes)
            total_rels = len(rels)

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_START,
                data=msgpack_encode({"total_nodes": total_nodes, "total_rels": total_rels}),
            )

            for i in range(0, total_nodes, BATCH_SIZE):
                batch = nodes[i : i + BATCH_SIZE]
                yield KGStreamEvent(
                    event_type=KGEventType.NODES_BATCH,
                    data=msgpack_encode(batch),
                    total=total_nodes,
                    current=min(i + BATCH_SIZE, total_nodes),
                )

            for i in range(0, total_rels, BATCH_SIZE):
                batch = rels[i : i + BATCH_SIZE]
                yield KGStreamEvent(
                    event_type=KGEventType.RELS_BATCH,
                    data=msgpack_encode(batch),
                    total=total_rels,
                    current=min(i + BATCH_SIZE, total_rels),
                )

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_END,
                data=msgpack_encode({"success": True}),
            )
        except Exception as e:
            logger.error(f"流式获取初始图数据失败: {e}")
            yield KGStreamEvent(
                event_type=KGEventType.ERROR,
                data=msgpack_encode({"error": str(e)}),
            )

    async def stream_search(
        self,
        query: str,
        top_k: int = 10,
        search_type: str = "hybrid",
        tenant_id: int | None = None,
    ) -> AsyncGenerator[KGStreamEvent, None]:
        """流式搜索知识图谱"""
        try:
            yield KGStreamEvent(
                event_type=KGEventType.STREAM_START,
                data=msgpack_encode({"query": query}),
            )

            result = self.search_by_text(
                query=query,
                top_k=top_k,
                search_type=search_type,
                tenant_id=tenant_id,
            )

            yield KGStreamEvent(
                event_type=KGEventType.SEARCH_RESULT,
                data=msgpack_encode(result),
            )

            yield KGStreamEvent(
                event_type=KGEventType.STREAM_END,
                data=msgpack_encode({"success": True}),
            )
        except Exception as e:
            logger.error(f"流式搜索失败: {e}")
            yield KGStreamEvent(
                event_type=KGEventType.ERROR,
                data=msgpack_encode({"error": str(e)}),
            )

@dataclass(frozen=True, slots=True)
class SyncScope:
    metalake: str
    catalog: str | None = None
    schema: str | None = None
    table: str | None = None

    def level(self) -> str:
        if self.table:
            return LEVEL_TABLE
        if self.schema:
            return LEVEL_SCHEMA
        if self.catalog:
            return LEVEL_CATALOG
        return LEVEL_ALL

    def node_payload(self) -> dict[str, Any]:
        return {
            "level": self.level(),
            "metalake": self.metalake,
            "catalog": self.catalog,
            "schema": self.schema,
            "table": self.table,
        }


class SyncProgressReporter:
    def __init__(
        self,
        *,
        scope: SyncScope,
        emitter: Callable[[dict[str, Any]], Awaitable[None]],
    ) -> None:
        self._scope = scope
        self._emitter = emitter
        self._totals = {SYNC_STEP: 0, EMBEDDING_STEP: 0}
        self._done = {SYNC_STEP: 0, EMBEDDING_STEP: 0}
        self._last_percent = {SYNC_STEP: -1, EMBEDDING_STEP: -1}

    async def init(self, *, sync_total: int, embedding_total: int) -> None:
        self._totals[SYNC_STEP] = max(sync_total, 0)
        self._totals[EMBEDDING_STEP] = max(embedding_total, 0)
        await self._emit_percent(SYNC_STEP)
        await self._emit_percent(EMBEDDING_STEP)

    async def advance_sync(self, count: int = 1) -> None:
        if count <= 0:
            return
        self._done[SYNC_STEP] += count
        await self._emit_percent(SYNC_STEP)

    async def advance_embedding(self, count: int = 1) -> None:
        if count <= 0:
            return
        self._done[EMBEDDING_STEP] += count
        await self._emit_percent(EMBEDDING_STEP)

    async def _emit_percent(self, step: str) -> None:
        total = self._totals.get(step, 0)
        done = self._done.get(step, 0)
        if total <= 0:
            percent = 100
        else:
            percent = int(min(100, max(0, done * 100 / total)))
        if percent == self._last_percent.get(step):
            return
        payload = {
            "ts": int(time.time() * 1000),
            "step": step,
            "percent": percent,
            "node": self._scope.node_payload(),
        }
        await self._emitter(payload)
        self._last_percent[step] = percent


class GravitinoSyncService:
    """Gravitino 元数据同步服务（按需触发，可重复执行）"""

    def __init__(
        self,
        *,
        scope: SyncScope | None = None,
        reporter: SyncProgressReporter | None = None,
        tenant_id: int | None = None,
    ):
        resolved_scope = scope or SyncScope(metalake=settings.gravitino_sync_metalake)
        self._scope = resolved_scope
        self._tenant_id = tenant_id or get_default_tenant_id()
        self._metalake_name = resolved_scope.metalake
        self._catalog_name = resolved_scope.catalog
        self._schema_name = resolved_scope.schema
        self._table_name = resolved_scope.table
        self._reporter = reporter
        self._metalake_id: int | None = None
        self._stats = {
            "catalogs": 0,
            "schemas": 0,
            "tables": 0,
            "columns": 0,
            "metrics": 0,
            "wordroots": 0,
            "modifiers": 0,
            "units": 0,
            "valuedomains": 0,
            "tags": 0,
            "tag_relationships": 0,
            "metric_column_lineage": 0,
            "column_valuedomain_lineage": 0,
            "embedding_tasks_queued": 0,
            "embedding_tasks_skipped": 0,
        }
        # 已有正确 embedding 的节点 ID 集合（provider 匹配才算有效）
        self._valid_embeddings: set[str] = set()
        # 当前 embedding provider 标识
        self._current_embedding_provider: str = ""

    @staticmethod
    def _parse_tags(raw: str | None) -> list[str]:
        if not raw:
            return []
        tags = [tag.strip() for tag in raw.split(",") if tag and tag.strip()]
        if not tags:
            return []
        seen: set[str] = set()
        result: list[str] = []
        for tag in tags:
            if tag in seen:
                continue
            seen.add(tag)
            result.append(tag)
        return result

    def _collect_tag_names(
        self,
        *,
        catalogs: list[dict[str, Any]],
        schemas: list[dict[str, Any]],
        tables: list[dict[str, Any]],
        columns_by_table: dict[int, list[dict[str, Any]]],
    ) -> set[str]:
        tag_names: set[str] = set()
        for row in catalogs:
            tag_names.update(self._parse_tags(row.get("tags")))
        for row in schemas:
            tag_names.update(self._parse_tags(row.get("tags")))
        for row in tables:
            tag_names.update(self._parse_tags(row.get("tags")))
            for col in columns_by_table.get(row["table_id"], []):
                tag_names.update(self._parse_tags(col.get("tags")))
        return tag_names

    def _append_scope_conditions(
        self,
        clauses: list[str],
        params: dict[str, Any],
        *,
        catalog_alias: str | None = None,
        schema_alias: str | None = None,
        table_alias: str | None = None,
    ) -> None:
        if self._catalog_name and catalog_alias:
            clauses.append(f"{catalog_alias}.catalog_name = :catalog_name")
            params["catalog_name"] = self._catalog_name
        if self._schema_name and schema_alias:
            clauses.append(f"{schema_alias}.schema_name = :schema_name")
            params["schema_name"] = self._schema_name
        if self._table_name and table_alias:
            clauses.append(f"{table_alias}.table_name = :table_name")
            params["table_name"] = self._table_name

    def _should_queue_embedding(self, node_id: str, description: str | None) -> bool:
        if not description or not description.strip():
            return False
        return node_id not in self._valid_embeddings

    def _should_queue_tag_embedding(self, node_id: str) -> bool:
        return node_id not in self._valid_embeddings

    async def _advance_sync(self, count: int = 1) -> None:
        if self._reporter is None:
            return
        await self._reporter.advance_sync(count=count)

    async def _advance_embedding(self, count: int = 1) -> None:
        if self._reporter is None:
            return
        await self._reporter.advance_embedding(count=count)

    async def _load_valid_embeddings(self, session) -> None:
        """
        加载 Neo4j 中 embedding provider 与当前配置匹配的节点 ID

        只有 provider 匹配的节点才跳过向量化，避免：
        1. 重复向量化浪费 API 费用
        2. 切换模型后数据不一致
        """
        from src.infrastructure.llm.embeddings import UnifiedEmbedder

        try:
            embedder = UnifiedEmbedder(self._tenant_id)
            self._current_embedding_provider = f"{embedder.provider}/{embedder.model_name}"
        except Exception as e:
            logger.warning(
                "gravitino_sync_embedder_init_failed",
                extra={"data": {"error": str(e)}},
            )
            self._current_embedding_provider = ""
            return

        self._valid_embeddings = await Metadata.list_embedding_ids(
            session,
            provider=self._current_embedding_provider,
        )
        stale_count = await Metadata.count_stale_embeddings(
            session,
            provider=self._current_embedding_provider,
        )

        logger.info(
            "gravitino_sync_embedding_check",
            extra={
                "data": {
                    "current_provider": self._current_embedding_provider,
                    "valid_count": len(self._valid_embeddings),
                    "stale_count": stale_count,
                }
            },
        )

    async def _queue_embedding_task(
        self,
        node_id: str,
        node_label: str,
        name: str,
        description: str | None = None,
        tags: list[str] | None = None,
    ) -> None:
        """将 embedding 任务入队（跳过无描述或 provider 匹配的节点）"""
        # 没有描述的节点不做 embedding，避免低质量向量误导检索
        if not description or not description.strip():
            self._stats["embedding_tasks_skipped"] += 1
            return

        # 跳过已有正确 embedding 的节点（provider 匹配）
        if node_id in self._valid_embeddings:
            self._stats["embedding_tasks_skipped"] += 1
            return

        parts = [name, description]
        if tags:
            parts.extend(tags)
        text = " ".join(parts)
        embedding_processor = get_embedding_processor()
        if await embedding_processor.put(node_id, node_label, text, tenant_id=self._tenant_id):
            self._stats["embedding_tasks_queued"] += 1

    async def sync_physical(self) -> dict:
        """同步物理资产（Catalog/Schema/Table/Column/Tag）"""
        self._ensure_metalake_id()
        logger.info(
            "gravitino_sync_start",
            extra={"data": {"metalake": self._metalake_name, "mode": "physical"}},
        )

        async with neo4j_async_session() as session:
            await self._load_valid_embeddings(session)

            catalogs = self._load_catalog_rows()
            schemas = self._load_schema_rows()
            tables = self._load_table_rows()
            columns_by_table = self._load_columns_by_table(tables)
            tag_names = self._collect_tag_names(
                catalogs=catalogs,
                schemas=schemas,
                tables=tables,
                columns_by_table=columns_by_table,
            )
            tags = self._load_tag_rows(tag_names)

            sync_total = (
                len(catalogs)
                + len(schemas)
                + len(tables)
                + sum(len(cols) for cols in columns_by_table.values())
                + len(tags)
            )
            embedding_total = self._calc_physical_embedding_total(
                catalogs=catalogs,
                schemas=schemas,
                tables=tables,
                columns_by_table=columns_by_table,
                tags=tags,
            )
            if self._reporter is not None:
                await self._reporter.init(
                    sync_total=sync_total,
                    embedding_total=embedding_total,
                )

            await self._sync_catalogs(session, rows=catalogs)
            await self._sync_schemas(session, rows=schemas)
            await self._sync_table_cols(
                session,
                tables=tables,
                columns_by_table=columns_by_table,
            )
            await self._sync_tag_nodes(session, rows=tags)
            await self._sync_tag_relationships(
                session,
                catalogs=catalogs,
                schemas=schemas,
                tables=tables,
                columns_by_table=columns_by_table,
            )

        logger.info(
            "gravitino_sync_complete",
            extra={"data": {"stats": self._stats, "mode": "physical"}},
        )
        return self._stats

    async def sync_semantic(self) -> dict:
        """同步语义资产（Metric/WordRoot/Modifier/Unit/ValueDomain）"""
        self._ensure_metalake_id()
        logger.info(
            "gravitino_sync_start",
            extra={"data": {"metalake": self._metalake_name, "mode": "semantic"}},
        )

        async with neo4j_async_session() as session:
            await self._load_valid_embeddings(session)

            metrics = self._load_metric_rows()
            wordroots = self._load_wordroot_rows()
            modifiers = self._load_modifier_rows()
            units = self._load_unit_rows()
            domain_lineage_rows = self._load_domain_lineage_rows()
            domain_codes = {row["domain_code"] for row in domain_lineage_rows}
            valuedomains = self._load_valuedomain_rows(domain_codes or None)

            sync_total = (
                len(metrics)
                + len(wordroots)
                + len(modifiers)
                + len(units)
                + len(valuedomains)
            )
            embedding_total = self._calc_semantic_embedding_total(
                metrics=metrics,
                wordroots=wordroots,
                modifiers=modifiers,
                units=units,
                valuedomains=valuedomains,
            )
            if self._reporter is not None:
                await self._reporter.init(
                    sync_total=sync_total,
                    embedding_total=embedding_total,
                )

            await self._sync_metrics(session, rows=metrics)
            await self._sync_wordroots(session, rows=wordroots)
            await self._sync_modifiers(session, rows=modifiers)
            await self._sync_units(session, rows=units)
            await self._sync_valuedomains(session, rows=valuedomains)

            await self._sync_metric_lineage(session)
            await self._sync_domain_lineage(session, rows=domain_lineage_rows)

        logger.info(
            "gravitino_sync_complete",
            extra={"data": {"stats": self._stats, "mode": "semantic"}},
        )
        return self._stats

    async def sync_all(self) -> dict:
        """执行一次全量同步（可重复调用）"""
        await self.sync_physical()
        await self.sync_semantic()
        return self._stats

    def _get_metalake_id(self) -> int | None:
        """获取 metalake ID"""
        query = """
        SELECT metalake_id
        FROM metalake_meta
        WHERE metalake_name = :name AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"name": self._metalake_name})
        return rows[0]["metalake_id"] if rows else None

    def _ensure_metalake_id(self) -> None:
        """确保 metalake_id 已解析"""
        if self._metalake_id is not None:
            return
        self._metalake_id = self._get_metalake_id()
        if not self._metalake_id:
            logger.error(
                "gravitino_sync_metalake_not_found",
                extra={"data": {"metalake": self._metalake_name}},
            )
            raise ValueError(f"Metalake '{self._metalake_name}' not found in Gravitino")
        logger.info(
            "gravitino_sync_metalake_found",
            extra={"data": {"metalake_id": self._metalake_id}},
        )

    def _calc_physical_embedding_total(
        self,
        *,
        catalogs: list[dict[str, Any]],
        schemas: list[dict[str, Any]],
        tables: list[dict[str, Any]],
        columns_by_table: dict[int, list[dict[str, Any]]],
        tags: list[dict[str, Any]],
    ) -> int:
        total = 0
        for row in catalogs:
            node_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            if self._should_queue_embedding(node_id, row.get("catalog_comment")):
                total += 1
        for row in schemas:
            node_id = generate_id(
                "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
            )
            if self._should_queue_embedding(node_id, row.get("schema_comment")):
                total += 1
        for row in tables:
            node_id = generate_id(
                "table",
                self._metalake_name,
                row["catalog_name"],
                row["schema_name"],
                row["table_name"],
            )
            if self._should_queue_embedding(node_id, row.get("table_comment")):
                total += 1
            cols = columns_by_table.get(row["table_id"]) or []
            for col in cols:
                col_id = generate_id(
                    "column",
                    self._metalake_name,
                    row["catalog_name"],
                    row["schema_name"],
                    row["table_name"],
                    col["column_name"],
                )
                if self._should_queue_embedding(col_id, col.get("column_comment")):
                    total += 1
        for row in tags:
            tag_id = generate_id("tag", self._metalake_name, row["tag_name"])
            if self._should_queue_tag_embedding(tag_id):
                total += 1
        return total

    def _calc_semantic_embedding_total(
        self,
        *,
        metrics: list[dict[str, Any]],
        wordroots: list[dict[str, Any]],
        modifiers: list[dict[str, Any]],
        units: list[dict[str, Any]],
        valuedomains: list[dict[str, Any]],
    ) -> int:
        total = 0
        for row in metrics:
            node_id = generate_id("metric", row["metric_code"])
            if self._should_queue_embedding(node_id, row.get("metric_comment")):
                total += 1
        for row in wordroots:
            node_id = generate_id("wordroot", row["root_code"])
            if self._should_queue_embedding(node_id, row.get("root_comment")):
                total += 1
        for row in modifiers:
            node_id = generate_id("modifier", row["modifier_code"])
            if self._should_queue_embedding(node_id, row.get("modifier_comment")):
                total += 1
        for row in units:
            node_id = generate_id("unit", row["unit_code"])
            if self._should_queue_embedding(node_id, row.get("unit_comment")):
                total += 1
        for row in valuedomains:
            node_id = generate_id("valuedomain", row["domain_code"])
            if self._should_queue_embedding(node_id, row.get("domain_comment")):
                total += 1
        return total

    def _load_catalog_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT cm.catalog_id, cm.catalog_name, cm.type, cm.provider, cm.catalog_comment, cm.properties,
               GROUP_CONCAT(t.tag_name) as tags
        FROM catalog_meta cm
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = cm.catalog_id
            AND tr.metadata_object_type = 'CATALOG'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta t ON tr.tag_id = t.tag_id
            AND t.deleted_at = 0
            AND t.tag_name NOT LIKE 'vd:%%'
        WHERE cm.metalake_id = :metalake_id AND cm.deleted_at = 0
        GROUP BY cm.catalog_id, cm.catalog_name, cm.type, cm.provider, cm.catalog_comment, cm.properties
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="cm")
        if clauses:
            query += " AND " + " AND ".join(clauses)
        return GravitinoDBClient.execute_query(query, params)

    def _load_schema_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT s.schema_id, s.schema_name, s.schema_comment, s.properties,
               c.catalog_name,
               GROUP_CONCAT(t.tag_name) as tags
        FROM schema_meta s
        JOIN catalog_meta c ON s.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = s.schema_id
            AND tr.metadata_object_type = 'SCHEMA'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta t ON tr.tag_id = t.tag_id
            AND t.deleted_at = 0
            AND t.tag_name NOT LIKE 'vd:%%'
        WHERE s.metalake_id = :metalake_id AND s.deleted_at = 0
        GROUP BY s.schema_id, s.schema_name, s.schema_comment, s.properties, c.catalog_name
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="c", schema_alias="s")
        if clauses:
            query += " AND " + " AND ".join(clauses)
        return GravitinoDBClient.execute_query(query, params)

    def _load_table_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT t.table_id, t.table_name, t.table_comment, t.current_version,
               s.schema_name, c.catalog_name,
               GROUP_CONCAT(tag.tag_name) as tags
        FROM table_meta t
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = t.table_id
            AND tr.metadata_object_type = 'TABLE'
            AND tr.deleted_at = 0
        LEFT JOIN tag_meta tag ON tr.tag_id = tag.tag_id
            AND tag.deleted_at = 0
            AND tag.tag_name NOT LIKE 'vd:%%'
        WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
        GROUP BY t.table_id, t.table_name, t.table_comment, t.current_version,
                 s.schema_name, c.catalog_name
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="c", schema_alias="s", table_alias="t")
        if clauses:
            query += " AND " + " AND ".join(clauses)
        return GravitinoDBClient.execute_query(query, params)

    def _load_columns_by_table(
        self, tables: list[dict[str, Any]]
    ) -> dict[int, list[dict[str, Any]]]:
        columns_by_table: dict[int, list[dict[str, Any]]] = {}
        for table in tables:
            column_query = """
            SELECT col.column_id, col.column_name, col.column_type, col.column_comment,
                   col.column_nullable, col.column_auto_increment, col.column_default_value,
                   GROUP_CONCAT(tag.tag_name) as tags
            FROM table_column_version_info col
            LEFT JOIN tag_relation_meta tr ON tr.metadata_object_id = col.column_id
                AND tr.metadata_object_type = 'COLUMN'
                AND tr.deleted_at = 0
            LEFT JOIN tag_meta tag ON tr.tag_id = tag.tag_id
                AND tag.deleted_at = 0
                AND tag.tag_name NOT LIKE 'vd:%%'
            WHERE col.table_id = :table_id AND col.table_version = :version
                  AND col.deleted_at = 0 AND col.column_op_type != 3
            GROUP BY col.column_id, col.column_name, col.column_type, col.column_comment,
                     col.column_nullable, col.column_auto_increment, col.column_default_value,
                     col.column_position
            ORDER BY col.column_position
            """
            rows = GravitinoDBClient.execute_query(
                column_query,
                {"table_id": table["table_id"], "version": table["current_version"]},
            )
            columns_by_table[table["table_id"]] = rows
        return columns_by_table

    def _load_tag_rows(self, tag_names: set[str] | None = None) -> list[dict[str, Any]]:
        query = """
        SELECT tag_id, tag_name, tag_comment, properties
        FROM tag_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
            AND tag_name NOT LIKE 'vd:%%'
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        if tag_names is not None:
            if not tag_names:
                return []
            placeholders: list[str] = []
            for idx, tag_name in enumerate(sorted(tag_names)):
                key = f"tag_name_{idx}"
                params[key] = tag_name
                placeholders.append(f":{key}")
            query += f" AND tag_name IN ({', '.join(placeholders)})"
        return GravitinoDBClient.execute_query(query, params)

    def _load_metric_rows(self) -> list[dict[str, Any]]:
        table_join = ""
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        if self._table_name:
            table_join = "JOIN table_meta t ON v.ref_table_id = t.table_id AND t.deleted_at = 0"
        query = f"""
        SELECT m.metric_id, m.metric_name, m.metric_code, m.metric_type,
               m.data_type, m.metric_comment, m.current_version,
               v.metric_unit, v.calculation_formula, v.parent_metric_codes,
               s.schema_name, c.catalog_name
        FROM metric_meta m
        JOIN schema_meta s ON m.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON m.catalog_id = c.catalog_id AND c.deleted_at = 0
        LEFT JOIN metric_version_info v ON m.metric_id = v.metric_id
            AND m.current_version = v.version AND v.deleted_at = 0
        {table_join}
        WHERE m.metalake_id = :metalake_id AND m.deleted_at = 0
        """
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="c", schema_alias="s")
        if self._table_name:
            clauses.append("t.table_name = :table_name")
            params["table_name"] = self._table_name
        if clauses:
            query += " AND " + " AND ".join(clauses)
        return GravitinoDBClient.execute_query(query, params)

    def _load_wordroot_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT root_code, root_name, data_type, root_comment
        FROM wordroot_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        return GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

    def _load_modifier_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT modifier_code, modifier_name, modifier_type, modifier_comment
        FROM metric_modifier_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        return GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

    def _load_unit_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT unit_code, unit_name, unit_symbol, unit_comment
        FROM unit_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        return GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

    def _load_valuedomain_rows(
        self, domain_codes: set[str] | None
    ) -> list[dict[str, Any]]:
        query = """
        SELECT domain_code, domain_name, domain_type, domain_level,
               items, data_type, domain_comment
        FROM value_domain_meta
        WHERE metalake_id = :metalake_id AND deleted_at = 0
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        if domain_codes:
            placeholders: list[str] = []
            for idx, code in enumerate(sorted(domain_codes)):
                key = f"domain_code_{idx}"
                params[key] = code
                placeholders.append(f":{key}")
            query += f" AND domain_code IN ({', '.join(placeholders)})"
        return GravitinoDBClient.execute_query(query, params)

    def _load_domain_lineage_rows(self) -> list[dict[str, Any]]:
        query = """
        SELECT t.tag_name, tr.metadata_object_id, tr.metadata_object_type
        FROM tag_meta t
        JOIN tag_relation_meta tr ON t.tag_id = tr.tag_id AND tr.deleted_at = 0
        JOIN table_column_version_info col ON tr.metadata_object_id = col.column_id
            AND col.deleted_at = 0 AND col.column_op_type != 3
        JOIN table_meta tbl ON col.table_id = tbl.table_id
            AND col.table_version = tbl.current_version
            AND tbl.deleted_at = 0
        JOIN schema_meta s ON tbl.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON tbl.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
            AND t.tag_name LIKE 'vd:%%'
            AND tr.metadata_object_type = 'COLUMN'
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="c", schema_alias="s", table_alias="tbl")
        if clauses:
            query += " AND " + " AND ".join(clauses)
        rows = GravitinoDBClient.execute_query(query, params)
        results: list[dict[str, Any]] = []
        for row in rows:
            tag_name = row.get("tag_name") or ""
            if not tag_name.startswith("vd:"):
                continue
            row["domain_code"] = tag_name[3:]
            results.append(row)
        return results

    async def _sync_catalogs(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 Catalog"""
        rows = rows or self._load_catalog_rows()

        for row in rows:
            catalog_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            properties = json.loads(row["properties"]) if row["properties"] else None
            tags = self._parse_tags(row.get("tags"))
            should_queue_embedding = self._should_queue_embedding(
                catalog_id, row.get("catalog_comment")
            )

            await Metadata.upsert_catalog(
                session,
                id=catalog_id,
                name=row["catalog_name"],
                metalake=self._metalake_name,
                created_by="GRAVITINO_SYNC",
                catalog_type=row["type"],
                provider=row["provider"],
                description=row["catalog_comment"],
                properties=json.dumps(properties) if properties else None,
            )
            self._stats["catalogs"] += 1
            await self._advance_sync()

            # 入队 embedding（带 tags）
            await self._queue_embedding_task(
                catalog_id, "Catalog", row["catalog_name"], row["catalog_comment"], tags
            )
            if should_queue_embedding:
                await self._advance_embedding()

        logger.debug(
            "gravitino_sync_catalogs",
            extra={"data": {"count": self._stats["catalogs"]}},
        )

    async def _sync_schemas(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 Schema"""
        rows = rows or self._load_schema_rows()

        for row in rows:
            schema_id = generate_id(
                "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
            )
            catalog_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            properties = json.loads(row["properties"]) if row["properties"] else None
            tags = self._parse_tags(row.get("tags"))
            should_queue_embedding = self._should_queue_embedding(
                schema_id, row.get("schema_comment")
            )
            await Metadata.upsert_schema(
                session,
                id=schema_id,
                name=row["schema_name"],
                description=row["schema_comment"],
                properties=json.dumps(properties) if properties else None,
                created_by="GRAVITINO_SYNC",
            )
            await Lineage.link_catalog_schema(
                session,
                catalog_id=catalog_id,
                schema_id=schema_id,
            )
            self._stats["schemas"] += 1
            await self._advance_sync()

            # 入队 embedding（带 tags）
            await self._queue_embedding_task(
                schema_id, "Schema", row["schema_name"], row["schema_comment"], tags
            )
            if should_queue_embedding:
                await self._advance_embedding()

        logger.debug(
            "gravitino_sync_schemas",
            extra={"data": {"count": self._stats["schemas"]}},
        )

    async def _sync_table_cols(
        self,
        session: AsyncSession,
        *,
        tables: list[dict[str, Any]] | None = None,
        columns_by_table: dict[int, list[dict[str, Any]]] | None = None,
    ) -> None:
        """同步 Table 和 Column"""
        tables = tables or self._load_table_rows()
        if columns_by_table is None:
            columns_by_table = self._load_columns_by_table(tables)

        for table in tables:
            table_id = generate_id(
                "table",
                self._metalake_name,
                table["catalog_name"],
                table["schema_name"],
                table["table_name"],
            )
            schema_id = generate_id(
                "schema", self._metalake_name, table["catalog_name"], table["schema_name"]
            )
            table_tags = self._parse_tags(table.get("tags"))
            should_queue_table = self._should_queue_embedding(
                table_id, table.get("table_comment")
            )

            await Metadata.upsert_table(
                session,
                id=table_id,
                name=table["table_name"],
                created_by="GRAVITINO_SYNC",
                payload=TableUpsertPayload(description=table.get("table_comment")),
            )
            await Lineage.link_schema_table(
                session,
                schema_id=schema_id,
                table_id=table_id,
            )
            self._stats["tables"] += 1
            await self._advance_sync()

            await self._queue_embedding_task(
                table_id,
                "Table",
                table["table_name"],
                table.get("table_comment"),
                table_tags,
            )
            if should_queue_table:
                await self._advance_embedding()

            columns = columns_by_table.get(table["table_id"]) or []
            column_ids: list[str] = []
            for col in columns:
                column_id = generate_id(
                    "column",
                    self._metalake_name,
                    table["catalog_name"],
                    table["schema_name"],
                    table["table_name"],
                    col["column_name"],
                )
                col_tags = self._parse_tags(col.get("tags"))
                should_queue_column = self._should_queue_embedding(
                    column_id, col.get("column_comment")
                )

                await Metadata.upsert_column_sync(
                    session,
                    id=column_id,
                    name=col["column_name"],
                    data_type=col["column_type"],
                    description=col["column_comment"],
                    nullable=bool(col["column_nullable"]),
                    auto_increment=bool(col["column_auto_increment"]),
                    default_value=col["column_default_value"],
                )
                column_ids.append(column_id)
                self._stats["columns"] += 1
                await self._advance_sync()

                await self._queue_embedding_task(
                    column_id,
                    "Column",
                    col["column_name"],
                    col["column_comment"],
                    col_tags,
                )
                if should_queue_column:
                    await self._advance_embedding()

            if column_ids:
                await Lineage.link_table_columns(
                    session,
                    table_id=table_id,
                    column_ids=column_ids,
                )

        logger.debug(
            "gravitino_sync_tables_columns",
            extra={
                "data": {
                    "tables": self._stats["tables"],
                    "columns": self._stats["columns"],
                }
            },
        )

    async def _sync_metrics(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 Metric"""
        rows = rows or self._load_metric_rows()

        for row in rows:
            metric_id = generate_id("metric", row["metric_code"])
            metric_type = row["metric_type"].upper()
            label = {
                "ATOMIC": "AtomicMetric",
                "DERIVED": "DerivedMetric",
                "COMPOSITE": "CompositeMetric",
            }.get(metric_type, "AtomicMetric")

            schema_id = generate_id(
                "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
            )

            await Metadata.upsert_metric_sync(
                session,
                label=label,
                id=metric_id,
                name=row["metric_name"],
                code=row["metric_code"],
                description=row["metric_comment"],
                unit=row["metric_unit"],
                calculation_formula=row["calculation_formula"],
                created_by="GRAVITINO_SYNC",
            )

            # 原子指标挂在 Schema 下
            if metric_type == "ATOMIC":
                await Lineage.link_schema_metric(
                    session,
                    schema_id=schema_id,
                    metric_id=metric_id,
                )

            should_queue_metric = self._should_queue_embedding(
                metric_id, row.get("metric_comment")
            )
            await self._queue_embedding_task(
                metric_id, label, row["metric_name"], row["metric_comment"]
            )
            if should_queue_metric:
                await self._advance_embedding()

            # 处理派生/复合指标的父子关系
            if row["parent_metric_codes"] and metric_type in ("DERIVED", "COMPOSITE"):
                # parent_metric_codes 是逗号分隔的字符串
                parent_codes = [
                    code.strip() for code in row["parent_metric_codes"].split(",") if code.strip()
                ]
                rel_type = "DERIVED_FROM" if metric_type == "DERIVED" else "COMPUTED_FROM"
                parent_ids = [generate_id("metric", parent_code) for parent_code in parent_codes]
                await Lineage.set_metric_parents(
                    session,
                    child_label=label,
                    child_id=metric_id,
                    rel_type=rel_type,
                    parent_ids=parent_ids,
                )

            self._stats["metrics"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_metrics",
            extra={"data": {"count": self._stats["metrics"]}},
        )

    async def _sync_wordroots(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 WordRoot"""
        rows = rows or self._load_wordroot_rows()

        for row in rows:
            wordroot_id = generate_id("wordroot", row["root_code"])
            dto = WordRootDTO(
                id=wordroot_id,
                name=row["root_name"] or row["root_code"],
                code=row["root_code"],
                data_type=row["data_type"],
                description=row["root_comment"],
                created_by="GRAVITINO_SYNC",
            )
            await Metadata.upsert_wordroot(session, dto)

            should_queue_wordroot = self._should_queue_embedding(
                wordroot_id, row.get("root_comment")
            )
            await self._queue_embedding_task(
                wordroot_id, "WordRoot", row["root_name"] or row["root_code"], row["root_comment"]
            )
            if should_queue_wordroot:
                await self._advance_embedding()

            self._stats["wordroots"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_wordroots",
            extra={"data": {"count": self._stats["wordroots"]}},
        )

    async def _sync_modifiers(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 Modifier"""
        rows = rows or self._load_modifier_rows()

        for row in rows:
            modifier_id = generate_id("modifier", row["modifier_code"])
            dto = ModifierDTO(
                id=modifier_id,
                name=row["modifier_name"] or row["modifier_code"],
                code=row["modifier_code"],
                modifier_type=row["modifier_type"],
                description=row["modifier_comment"],
                created_by="GRAVITINO_SYNC",
            )
            await Metadata.upsert_modifier(session, dto)

            should_queue_modifier = self._should_queue_embedding(
                modifier_id, row.get("modifier_comment")
            )
            await self._queue_embedding_task(
                modifier_id,
                "Modifier",
                row["modifier_name"] or row["modifier_code"],
                row["modifier_comment"],
            )
            if should_queue_modifier:
                await self._advance_embedding()

            self._stats["modifiers"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_modifiers",
            extra={"data": {"count": self._stats["modifiers"]}},
        )

    async def _sync_units(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 Unit"""
        rows = rows or self._load_unit_rows()

        for row in rows:
            unit_id = generate_id("unit", row["unit_code"])
            dto = UnitDTO(
                id=unit_id,
                name=row["unit_name"] or row["unit_code"],
                code=row["unit_code"],
                symbol=row["unit_symbol"],
                description=row["unit_comment"],
                created_by="GRAVITINO_SYNC",
            )
            await Metadata.upsert_unit(session, dto)

            should_queue_unit = self._should_queue_embedding(
                unit_id, row.get("unit_comment")
            )
            await self._queue_embedding_task(
                unit_id, "Unit", row["unit_name"] or row["unit_code"], row["unit_comment"]
            )
            if should_queue_unit:
                await self._advance_embedding()

            self._stats["units"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_units",
            extra={"data": {"count": self._stats["units"]}},
        )

    async def _sync_valuedomains(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步 ValueDomain"""
        rows = rows or self._load_valuedomain_rows(None)

        for row in rows:
            valuedomain_id = generate_id("valuedomain", row["domain_code"])
            dto = ValueDomainDTO(
                id=valuedomain_id,
                name=row["domain_name"] or row["domain_code"],
                code=row["domain_code"],
                domain_type=row["domain_type"],
                domain_level=row["domain_level"],
                items=row["items"],
                data_type=row["data_type"],
                description=row["domain_comment"],
                created_by="GRAVITINO_SYNC",
            )
            await Metadata.upsert_valuedomain(session, dto)

            # 将 ValueDomain embedding 任务入队（包含 items 信息）
            embedding_text = row["domain_name"] or row["domain_code"]
            if row["items"]:
                embedding_text += f" {row['items']}"
            should_queue_domain = self._should_queue_embedding(
                valuedomain_id, row.get("domain_comment")
            )
            await self._queue_embedding_task(
                valuedomain_id, "ValueDomain", embedding_text, row["domain_comment"]
            )
            if should_queue_domain:
                await self._advance_embedding()

            self._stats["valuedomains"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_valuedomains",
            extra={"data": {"count": self._stats["valuedomains"]}},
        )

    async def _sync_metric_lineage(self, session: AsyncSession) -> None:
        """同步原子指标与列的血缘关系 (MEASURES, FILTERS_BY)"""
        # 查询原子指标的版本信息，包含 ref 表和列信息
        query = """
        SELECT m.metric_code, m.metric_type,
               v.ref_table_id, v.measure_column_ids, v.filter_column_ids,
               t.table_name, t.current_version,
               s.schema_name, c.catalog_name
        FROM metric_meta m
        JOIN metric_version_info v ON m.metric_id = v.metric_id
            AND m.current_version = v.version AND v.deleted_at = 0
        JOIN table_meta t ON v.ref_table_id = t.table_id AND t.deleted_at = 0
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE m.metalake_id = :metalake_id AND m.deleted_at = 0
            AND m.metric_type = 'ATOMIC'
            AND v.ref_table_id IS NOT NULL
        """
        params: dict[str, Any] = {"metalake_id": self._metalake_id}
        clauses: list[str] = []
        self._append_scope_conditions(clauses, params, catalog_alias="c", schema_alias="s", table_alias="t")
        if clauses:
            query += " AND " + " AND ".join(clauses)
        rows = GravitinoDBClient.execute_query(query, params)

        for row in rows:
            metric_id = generate_id("metric", row["metric_code"])
            ref_table_id = row["ref_table_id"]
            ref_catalog = row["catalog_name"]
            ref_schema = row["schema_name"]
            ref_table = row["table_name"]
            table_version = row["current_version"]

            measure_ids = self._parse_metric_column_ids(row["measure_column_ids"])
            filter_ids = self._parse_metric_column_ids(row["filter_column_ids"])
            if not measure_ids and not filter_ids:
                continue

            combined_ids = self._dedupe_ids(measure_ids + filter_ids)
            column_name_map = self._load_column_name_map(
                ref_table_id, table_version, combined_ids
            )

            for col_id in self._dedupe_ids(measure_ids):
                col_name = column_name_map.get(col_id)
                if not col_name:
                    continue
                column_id = generate_id(
                    "column",
                    self._metalake_name,
                    ref_catalog,
                    ref_schema,
                    ref_table,
                    col_name,
                )
                await Lineage.add_metric_measure(
                    session,
                    metric_id=metric_id,
                    column_id=column_id,
                )
                self._stats["metric_column_lineage"] += 1

            for col_id in self._dedupe_ids(filter_ids):
                col_name = column_name_map.get(col_id)
                if not col_name:
                    continue
                column_id = generate_id(
                    "column",
                    self._metalake_name,
                    ref_catalog,
                    ref_schema,
                    ref_table,
                    col_name,
                )
                await Lineage.add_metric_filter(
                    session,
                    metric_id=metric_id,
                    column_id=column_id,
                )
                self._stats["metric_column_lineage"] += 1

        logger.debug(
            "gravitino_sync_metric_column_lineage",
            extra={"data": {"count": self._stats["metric_column_lineage"]}},
        )

    @staticmethod
    def _parse_metric_column_ids(raw: str | None) -> list[int]:
        if not raw:
            return []
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return []
        if not isinstance(data, list):
            return []

        ids: list[int] = []
        for item in data:
            if isinstance(item, dict):
                candidate = item.get("id") or item.get("column_id") or item.get("columnId")
            else:
                candidate = item

            if isinstance(candidate, bool):
                continue
            if isinstance(candidate, (int,)):
                ids.append(candidate)
                continue
            if isinstance(candidate, str) and candidate.isdigit():
                ids.append(int(candidate))

        return ids

    @staticmethod
    def _dedupe_ids(ids: list[int]) -> list[int]:
        seen: set[int] = set()
        result: list[int] = []
        for item in ids:
            if item in seen:
                continue
            seen.add(item)
            result.append(item)
        return result

    @staticmethod
    def _load_column_name_map(
        table_id: int, table_version: int, column_ids: list[int]
    ) -> dict[int, str]:
        if not column_ids:
            return {}

        params: dict[str, int] = {
            "table_id": table_id,
            "table_version": table_version,
        }
        placeholders: list[str] = []
        for idx, col_id in enumerate(column_ids):
            key = f"col_id_{idx}"
            params[key] = col_id
            placeholders.append(f":{key}")

        query = f"""
        SELECT column_id, column_name
        FROM table_column_version_info
        WHERE table_id = :table_id AND table_version = :table_version
            AND deleted_at = 0 AND column_op_type != 3
            AND column_id IN ({", ".join(placeholders)})
        """
        rows = GravitinoDBClient.execute_query(query, params)
        return {row["column_id"]: row["column_name"] for row in rows}

    async def _sync_domain_lineage(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """同步列与值域的血缘关系 (HAS_VALUE_DOMAIN)"""
        rows = rows or self._load_domain_lineage_rows()

        for row in rows:
            tag_name = row.get("tag_name") or ""
            domain_code = row.get("domain_code") or (tag_name[3:] if tag_name.startswith("vd:") else "")
            if not domain_code:
                continue
            column_object_id = row["metadata_object_id"]

            # 查询列的详细信息以生成 column_id
            column_query = """
            SELECT col.column_name, t.table_name, s.schema_name, c.catalog_name
            FROM table_column_version_info col
            JOIN table_meta t ON col.table_id = t.table_id AND t.deleted_at = 0
            JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
            JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
            WHERE col.column_id = :column_id AND col.deleted_at = 0
                AND col.column_op_type != 3
            LIMIT 1
            """
            col_rows = GravitinoDBClient.execute_query(
                column_query, {"column_id": column_object_id}
            )

            if not col_rows:
                continue

            col_info = col_rows[0]
            column_id = generate_id(
                "column",
                self._metalake_name,
                col_info["catalog_name"],
                col_info["schema_name"],
                col_info["table_name"],
                col_info["column_name"],
            )

            await Lineage.set_column_valuedomain(
                session,
                column_id=column_id,
                domain_code=domain_code,
            )
            self._stats["column_valuedomain_lineage"] += 1

        logger.debug(
            "gravitino_sync_column_valuedomain_lineage",
            extra={"data": {"count": self._stats["column_valuedomain_lineage"]}},
        )

    async def _sync_tag_nodes(
        self, session: AsyncSession, *, rows: list[dict[str, Any]] | None = None
    ) -> None:
        """
        同步 Tag 节点（排除 vd: 前缀的值域标签）

        从 tag_meta 表同步到 Neo4j Tag 节点
        """
        rows = rows or self._load_tag_rows()

        for row in rows:
            tag_id = generate_id("tag", self._metalake_name, row["tag_name"])
            properties = json.loads(row["properties"]) if row["properties"] else None

            await Metadata.upsert_tag(
                session,
                id=tag_id,
                name=row["tag_name"],
                description=row["tag_comment"],
                properties=properties,
                created_by="GRAVITINO_SYNC",
            )

            # Tag 向量化：name 本身有业务含义，不需要 description 作为必选项
            embedding_text = row["tag_name"]
            if row["tag_comment"]:
                embedding_text += f" {row['tag_comment']}"
            should_queue_tag = self._should_queue_tag_embedding(tag_id)
            await self._queue_tag_embedding(tag_id, embedding_text)
            if should_queue_tag:
                await self._advance_embedding()

            self._stats["tags"] += 1
            await self._advance_sync()

        logger.debug(
            "gravitino_sync_tag_nodes",
            extra={"data": {"count": self._stats["tags"]}},
        )

    async def _queue_tag_embedding(self, node_id: str, text: str) -> None:
        """Tag 向量化入队（name 本身有业务含义，不需要 description 必选）"""
        if node_id in self._valid_embeddings:
            self._stats["embedding_tasks_skipped"] += 1
            return

        embedding_processor = get_embedding_processor()
        if await embedding_processor.put(node_id, "Tag", text, tenant_id=self._tenant_id):
            self._stats["embedding_tasks_queued"] += 1

    async def _sync_tag_relationships(
        self,
        session: AsyncSession,
        *,
        catalogs: list[dict[str, Any]] | None = None,
        schemas: list[dict[str, Any]] | None = None,
        tables: list[dict[str, Any]] | None = None,
        columns_by_table: dict[int, list[dict[str, Any]]] | None = None,
    ) -> None:
        """
        同步 HAS_TAG 关系边（排除 vd: 前缀的值域标签）

        从 tag_relation_meta 表同步到 Neo4j HAS_TAG 关系
        """
        if catalogs is None or schemas is None or tables is None or columns_by_table is None:
            # 查询所有非 vd: 前缀的 tag 关联（兼容旧调用）
            query = """
            SELECT tr.metadata_object_id, tr.metadata_object_type, t.tag_name
            FROM tag_meta t
            JOIN tag_relation_meta tr ON t.tag_id = tr.tag_id AND tr.deleted_at = 0
            WHERE t.metalake_id = :metalake_id AND t.deleted_at = 0
                AND t.tag_name NOT LIKE 'vd:%%'
            """
            rows = GravitinoDBClient.execute_query(query, {"metalake_id": self._metalake_id})

            for row in rows:
                object_type = row["metadata_object_type"]
                object_id = row["metadata_object_id"]
                tag_name = row["tag_name"]

                tag_node_id = generate_id("tag", self._metalake_name, tag_name)

                # 根据对象类型查询详细信息并创建 HAS_TAG 关系
                if object_type == "TABLE":
                    await self._link_table_tag(session, object_id, tag_node_id)
                elif object_type == "COLUMN":
                    await self._link_column_tag(session, object_id, tag_node_id)
                elif object_type == "SCHEMA":
                    await self._link_schema_tag(session, object_id, tag_node_id)
                elif object_type == "CATALOG":
                    await self._link_catalog_tag(session, object_id, tag_node_id)

                self._stats["tag_relationships"] += 1

            logger.debug(
                "gravitino_sync_tag_relationships",
                extra={"data": {"count": self._stats["tag_relationships"]}},
            )
            return

        relationship_count = 0
        for row in catalogs:
            tag_names = self._parse_tags(row.get("tags"))
            if not tag_names:
                continue
            tag_ids = [generate_id("tag", self._metalake_name, tag) for tag in tag_names]
            source_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
            await Lineage.batch_add_tag(
                session,
                source_label="Catalog",
                source_id=source_id,
                tag_ids=tag_ids,
            )
            relationship_count += len(tag_ids)

        for row in schemas:
            tag_names = self._parse_tags(row.get("tags"))
            if not tag_names:
                continue
            tag_ids = [generate_id("tag", self._metalake_name, tag) for tag in tag_names]
            source_id = generate_id(
                "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
            )
            await Lineage.batch_add_tag(
                session,
                source_label="Schema",
                source_id=source_id,
                tag_ids=tag_ids,
            )
            relationship_count += len(tag_ids)

        for row in tables:
            tag_names = self._parse_tags(row.get("tags"))
            if tag_names:
                tag_ids = [generate_id("tag", self._metalake_name, tag) for tag in tag_names]
                source_id = generate_id(
                    "table",
                    self._metalake_name,
                    row["catalog_name"],
                    row["schema_name"],
                    row["table_name"],
                )
                await Lineage.batch_add_tag(
                    session,
                    source_label="Table",
                    source_id=source_id,
                    tag_ids=tag_ids,
                )
                relationship_count += len(tag_ids)

            columns = columns_by_table.get(row["table_id"], [])
            for col in columns:
                col_tags = self._parse_tags(col.get("tags"))
                if not col_tags:
                    continue
                tag_ids = [generate_id("tag", self._metalake_name, tag) for tag in col_tags]
                column_id = generate_id(
                    "column",
                    self._metalake_name,
                    row["catalog_name"],
                    row["schema_name"],
                    row["table_name"],
                    col["column_name"],
                )
                await Lineage.batch_add_tag(
                    session,
                    source_label="Column",
                    source_id=column_id,
                    tag_ids=tag_ids,
                )
                relationship_count += len(tag_ids)

        self._stats["tag_relationships"] += relationship_count

        logger.debug(
            "gravitino_sync_tag_relationships",
            extra={"data": {"count": self._stats["tag_relationships"]}},
        )

    async def _link_table_tag(self, session: AsyncSession, table_id: int, tag_node_id: str) -> None:
        """创建 Table -> Tag 的 HAS_TAG 关系"""
        query = """
        SELECT t.table_name, s.schema_name, c.catalog_name
        FROM table_meta t
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE t.table_id = :table_id AND t.deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"table_id": table_id})
        if not rows:
            return

        row = rows[0]
        source_id = generate_id(
            "table", self._metalake_name, row["catalog_name"], row["schema_name"], row["table_name"]
        )
        await Lineage.add_has_tag(
            session,
            source_label="Table",
            source_id=source_id,
            tag_id=tag_node_id,
        )

    async def _link_column_tag(
        self, session: AsyncSession, column_id: int, tag_node_id: str
    ) -> None:
        """创建 Column -> Tag 的 HAS_TAG 关系"""
        query = """
        SELECT col.column_name, t.table_name, s.schema_name, c.catalog_name
        FROM table_column_version_info col
        JOIN table_meta t ON col.table_id = t.table_id
            AND col.table_version = t.current_version
            AND t.deleted_at = 0
        JOIN schema_meta s ON t.schema_id = s.schema_id AND s.deleted_at = 0
        JOIN catalog_meta c ON t.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE col.column_id = :column_id AND col.deleted_at = 0
            AND col.column_op_type != 3
        LIMIT 1
        """
        rows = GravitinoDBClient.execute_query(query, {"column_id": column_id})
        if not rows:
            return

        row = rows[0]
        source_id = generate_id(
            "column",
            self._metalake_name,
            row["catalog_name"],
            row["schema_name"],
            row["table_name"],
            row["column_name"],
        )
        await Lineage.add_has_tag(
            session,
            source_label="Column",
            source_id=source_id,
            tag_id=tag_node_id,
        )

    async def _link_schema_tag(
        self, session: AsyncSession, schema_id: int, tag_node_id: str
    ) -> None:
        """创建 Schema -> Tag 的 HAS_TAG 关系"""
        query = """
        SELECT s.schema_name, c.catalog_name
        FROM schema_meta s
        JOIN catalog_meta c ON s.catalog_id = c.catalog_id AND c.deleted_at = 0
        WHERE s.schema_id = :schema_id AND s.deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"schema_id": schema_id})
        if not rows:
            return

        row = rows[0]
        source_id = generate_id(
            "schema", self._metalake_name, row["catalog_name"], row["schema_name"]
        )
        await Lineage.add_has_tag(
            session,
            source_label="Schema",
            source_id=source_id,
            tag_id=tag_node_id,
        )

    async def _link_catalog_tag(
        self, session: AsyncSession, catalog_id: int, tag_node_id: str
    ) -> None:
        """创建 Catalog -> Tag 的 HAS_TAG 关系"""
        query = """
        SELECT catalog_name
        FROM catalog_meta
        WHERE catalog_id = :catalog_id AND deleted_at = 0
        """
        rows = GravitinoDBClient.execute_query(query, {"catalog_id": catalog_id})
        if not rows:
            return

        row = rows[0]
        source_id = generate_id("catalog", self._metalake_name, row["catalog_name"])
        await Lineage.add_has_tag(
            session,
            source_label="Catalog",
            source_id=source_id,
            tag_id=tag_node_id,
        )


async def sync_gravitino_metadata(
    *,
    scope: SyncScope | None = None,
    reporter: SyncProgressReporter | None = None,
    mode: str = "all",
    tenant_id: int | None = None,
) -> dict:
    """执行 Gravitino 元数据同步（支持多次调用）"""
    service = GravitinoSyncService(scope=scope, reporter=reporter, tenant_id=tenant_id)
    if mode == "physical":
        return await service.sync_physical()
    if mode == "semantic":
        return await service.sync_semantic()
    return await service.sync_all()
