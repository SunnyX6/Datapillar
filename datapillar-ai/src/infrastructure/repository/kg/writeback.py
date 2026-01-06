"""
知识图谱写回（Writeback / Command Repository）

边界（强约束）：
- 这里只允许“写回/更新”知识图谱，不允许承载任何读查询逻辑。

目的：
- 避免把查询与写回混在一个 Repository 里
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import Any

from src.infrastructure.database import AsyncNeo4jClient
from src.infrastructure.database.cypher import arun_cypher
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)


class Neo4jKGWritebackRepository:
    """知识图谱写回仓储（只写，不读）"""

    @classmethod
    async def persist_kg_updates(
        cls,
        updates: list[dict[str, Any]],
        user_id: str,
        session_id: str,
    ) -> int:
        """
        将用户确认过的结构化事实写回 Neo4j（事务性写入）

        使用显式事务确保原子性：要么全部成功，要么全部回滚。
        支持类型: table_role / lineage / col_map / join
        """
        if not updates:
            return 0

        ts = datetime.now(UTC).isoformat()
        saved = 0

        driver = await AsyncNeo4jClient.get_driver()
        async with driver.session(database=settings.neo4j_database) as session:
            tx = await session.begin_transaction()
            try:
                for upd in updates:
                    upd_type = upd.get("type")
                    if upd_type == "table_role":
                        await arun_cypher(
                            tx,
                            """
                            MERGE (t:Table {name:$table})
                            MERGE (w:WorkflowSession {session_id:$session_id})
                            SET w.last_seen=$ts, w.user_id=$user_id
                            MERGE (t)-[r:ETL_ROLE {session_id:$session_id}]->(w)
                            SET r.type=$role, r.by_user=$user_id, r.ts=$ts, r.confidence=$confidence
                            """,
                            {
                                "table": upd.get("table"),
                                "role": upd.get("role"),
                                "user_id": user_id,
                                "session_id": session_id,
                                "ts": ts,
                                "confidence": float(upd.get("confidence", 0.5)),
                            },
                        )
                        saved += 1
                    elif upd_type == "lineage":
                        await arun_cypher(
                            tx,
                            """
                            MATCH (s:Table {name:$source_table})
                            MATCH (t:Table {name:$target_table})
                            MERGE (s)-[r:CONFIRMED_LINEAGE]->(t)
                            SET r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "col_map":
                        await arun_cypher(
                            tx,
                            """
                            MATCH (s:Table {name:$source_table})-[:HAS_COLUMN]->(sc:Column {name:$source_column})
                            MATCH (t:Table {name:$target_table})-[:HAS_COLUMN]->(tc:Column {name:$target_column})
                            MERGE (sc)-[r:CONFIRMED_MAP]->(tc)
                            SET r.transform=$transform, r.confidence=$confidence, r.by_user=$user_id, r.ts=$ts
                            """,
                            {
                                "source_table": upd.get("source_table"),
                                "target_table": upd.get("target_table"),
                                "source_column": upd.get("source_column"),
                                "target_column": upd.get("target_column"),
                                "transform": upd.get("transform", "direct"),
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    elif upd_type == "join":
                        await arun_cypher(
                            tx,
                            """
                            MATCH (l:Table {name:$left})
                            MATCH (r:Table {name:$right})
                            MERGE (l)-[j:JOIN_KEY]->(r)
                            SET j.on=$on, j.confidence=$confidence, j.by_user=$user_id, j.ts=$ts
                            """,
                            {
                                "left": upd.get("left"),
                                "right": upd.get("right"),
                                "on": upd.get("on") or [],
                                "confidence": float(upd.get("confidence", 0.5)),
                                "user_id": user_id,
                                "ts": ts,
                            },
                        )
                        saved += 1
                    else:
                        logger.warning("[persist_kg_updates] 忽略未知类型: %s", upd_type)

                await tx.commit()
                logger.info("[persist_kg_updates] 事务提交成功，写入 %s 条记录", saved)

            except Exception as exc:
                await tx.rollback()
                logger.error("[persist_kg_updates] 事务回滚: %s", exc)
                raise

        return saved

    @classmethod
    async def bump_sql_count(cls, sql_id: str) -> bool:
        """增加 SQL 节点的使用次数"""
        cypher = """
        MATCH (s:SQL {id: $sql_id})
        SET s.use_count = coalesce(s.use_count, 0) + 1,
            s.last_used = datetime()
        RETURN s.use_count as use_count
        """
        try:
            driver = await AsyncNeo4jClient.get_driver()
            async with driver.session(database=settings.neo4j_database) as session:
                result = await arun_cypher(session, cypher, {"sql_id": sql_id})
                record = await result.single()
                if record:
                    logger.info("SQL 使用次数更新: %s -> %s", sql_id, record["use_count"])
                    return True
                return False
        except Exception as e:
            logger.error("更新 SQL 使用次数失败: %s", e)
            return False
