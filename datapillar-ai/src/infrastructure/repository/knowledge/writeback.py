# @author Sunny
# @date 2026-01-27

"""
Knowledge graph write back(Writeback / Command Repository)

border(Strong constraints):- Only allowed here"write back/update"Knowledge graph,Not allowed to host any read query logic.purpose:- Avoid mixing queries with writeback Repository inside
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
    """Knowledge graph writes back to warehousing(write only,Dont read)"""

    @classmethod
    async def persist_kg_updates(
        cls,
        updates: list[dict[str, Any]],
        user_id: str,
        session_id: str,
    ) -> int:
        """
        Write back the structured facts confirmed by the user Neo4j(transactional write)

        Use explicit transactions to ensure atomicity:Either all succeed,Or roll it all back.Support type:table_role / lineage / col_map / join
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
                        logger.warning("[persist_kg_updates] Ignore unknown types:%s", upd_type)

                await tx.commit()
                logger.info(
                    "[persist_kg_updates] Transaction submitted successfully,write %s records",
                    saved,
                )

            except Exception as exc:
                await tx.rollback()
                logger.error("[persist_kg_updates] transaction rollback:%s", exc)
                raise

        return saved

    @classmethod
    async def bump_sql_count(cls, sql_id: str) -> bool:
        """increase SQL The number of times the node is used"""
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
                    logger.info("SQL Usage updates:%s -> %s", sql_id, record["use_count"])
                    return True
                return False
        except Exception as e:
            logger.error("update SQL Use count failed:%s", e)
            return False
