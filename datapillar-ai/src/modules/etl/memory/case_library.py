"""
案例库

管理历史成功案例和失败教训，用于学习和优化。
"""

import logging
from typing import List, Dict, Any, Optional
from datetime import datetime

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class EtlCase(BaseModel):
    """ETL 案例"""
    case_id: str
    user_query: str  # 用户原始查询
    source_tables: List[str] = Field(default_factory=list)
    target_tables: List[str] = Field(default_factory=list)
    intent: str  # ETL 意图
    sql_text: Optional[str] = None
    is_success: bool = True
    error_message: Optional[str] = None
    user_feedback: Optional[str] = None  # 用户反馈
    created_at: datetime = Field(default_factory=datetime.utcnow)
    tags: List[str] = Field(default_factory=list)


class CaseLibrary:
    """
    案例库

    从 Neo4j 的 ReferenceSQL 节点检索历史案例，
    支持相似案例匹配和学习。
    """

    def __init__(self):
        self._local_cases: List[EtlCase] = []  # 本地缓存（当前会话产生的案例）

    async def search_similar_cases(
        self,
        source_tables: List[str],
        target_tables: List[str],
        intent: Optional[str] = None,
        limit: int = 5,
    ) -> List[EtlCase]:
        """
        搜索相似案例

        从 Neo4j 的 ReferenceSQL 节点检索历史成功案例，
        基于源表、目标表和意图进行匹配。

        Args:
            source_tables: 源表列表
            target_tables: 目标表列表
            intent: ETL 意图
            limit: 返回数量

        Returns:
            相似案例列表
        """
        from src.infrastructure.repository import KnowledgeRepository

        try:
            # 构建标签过滤
            tags = [intent] if intent else None

            # 调用 KnowledgeRepository 检索历史参考 SQL
            results = await KnowledgeRepository.search_reference_sql(
                query=" ".join(source_tables + target_tables),
                source_tables=source_tables if source_tables else None,
                target_tables=target_tables if target_tables else None,
                tags=tags,
                limit=limit,
            )

            # 转换为 EtlCase 格式
            cases = []
            for r in results:
                case = EtlCase(
                    case_id=r.get("fingerprint", ""),
                    user_query=r.get("summary", ""),
                    source_tables=r.get("source_tables", []),
                    target_tables=r.get("target_tables", []),
                    intent=r.get("tags", [""])[0] if r.get("tags") else "",
                    sql_text=r.get("sql_text"),
                    is_success=True,
                    tags=r.get("tags", []),
                )
                cases.append(case)

            logger.info(f"检索到 {len(cases)} 个相似案例")
            return cases

        except Exception as e:
            logger.error(f"搜索相似案例失败: {e}")
            return []

    async def save_case(self, case: EtlCase) -> bool:
        """
        保存案例到案例库

        Args:
            case: ETL 案例

        Returns:
            是否保存成功
        """
        try:
            # 添加到本地缓存
            self._local_cases.append(case)

            # 如果是成功案例，同时保存到 Neo4j
            if case.is_success and case.sql_text:
                from src.infrastructure.repository import KnowledgeRepository

                await KnowledgeRepository.persist_kg_updates(
                    updates=[
                        {
                            "type": "reference_sql",
                            "sql": case.sql_text,
                            "summary": case.user_query,
                            "tags": case.tags + [case.intent],
                            "sources": case.source_tables,
                            "targets": case.target_tables,
                            "confidence": 0.8 if case.user_feedback else 0.6,
                        }
                    ],
                    user_id="system",
                    session_id=case.case_id,
                )

            logger.info(f"案例已保存: {case.case_id}")
            return True

        except Exception as e:
            logger.error(f"保存案例失败: {e}")
            return False

    def get_local_cases(self) -> List[EtlCase]:
        """获取本地缓存的案例"""
        return self._local_cases

    def get_success_cases(self) -> List[EtlCase]:
        """获取成功案例"""
        return [c for c in self._local_cases if c.is_success]

    def get_failed_cases(self) -> List[EtlCase]:
        """获取失败案例"""
        return [c for c in self._local_cases if not c.is_success]

    def clear_local(self) -> None:
        """清空本地缓存"""
        self._local_cases.clear()
