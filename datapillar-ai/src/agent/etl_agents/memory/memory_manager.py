"""
记忆管理器

统一管理知识缓存、短期记忆和长期记忆。
"""

import logging
from typing import Dict, Any, Optional, List

from src.agent.etl_agents.memory.knowledge_cache import KnowledgeCache
from src.agent.etl_agents.memory.case_library import CaseLibrary, EtlCase
from src.agent.etl_agents.schemas.context import KnowledgeContext, TableSchema, JoinHint

logger = logging.getLogger(__name__)


class MemoryManager:
    """
    记忆管理器

    统一管理：
    - KnowledgeCache: 知识缓存（表结构、血缘、JOIN 关系）
    - CaseLibrary: 案例库（历史成功/失败案例）
    - ShortTermMemory: 短期记忆（当前会话状态）
    """

    def __init__(self, cache_ttl_minutes: int = 30):
        self.knowledge_cache = KnowledgeCache(ttl_minutes=cache_ttl_minutes)
        self.case_library = CaseLibrary()

        # 短期记忆（当前会话）
        self._short_term: Dict[str, Any] = {}

    # ==================== 知识缓存代理方法 ====================

    def get_cached_table(self, table_name: str) -> Optional[TableSchema]:
        """获取缓存的表结构"""
        return self.knowledge_cache.get_table(table_name)

    def cache_table(self, table_name: str, table: TableSchema) -> None:
        """缓存表结构"""
        self.knowledge_cache.set_table(table_name, table)

    def get_cached_tables(self, table_names: List[str]) -> Dict[str, TableSchema]:
        """批量获取缓存的表结构"""
        return self.knowledge_cache.get_tables(table_names)

    def cache_tables(self, tables: Dict[str, TableSchema]) -> None:
        """批量缓存表结构"""
        for name, table in tables.items():
            self.knowledge_cache.set_table(name, table)

    def get_cached_join_hints(self, table_name: str) -> Optional[List[JoinHint]]:
        """获取缓存的 JOIN 关系"""
        return self.knowledge_cache.get_join_hints(table_name)

    def cache_join_hints(self, table_name: str, hints: List[JoinHint]) -> None:
        """缓存 JOIN 关系"""
        self.knowledge_cache.set_join_hints(table_name, hints)

    def get_cached_dq_rules(self, table_name: str) -> Optional[List[Dict[str, Any]]]:
        """获取缓存的 DQ 规则"""
        return self.knowledge_cache.get_dq_rules(table_name)

    def cache_dq_rules(self, table_name: str, rules: List[Dict[str, Any]]) -> None:
        """缓存 DQ 规则"""
        self.knowledge_cache.set_dq_rules(table_name, rules)

    # ==================== 案例库代理方法 ====================

    async def search_similar_cases(
        self,
        source_tables: List[str],
        target_tables: List[str],
        intent: Optional[str] = None,
        limit: int = 5,
    ) -> List[EtlCase]:
        """搜索相似案例"""
        return await self.case_library.search_similar_cases(
            source_tables=source_tables,
            target_tables=target_tables,
            intent=intent,
            limit=limit,
        )

    async def save_case(self, case: EtlCase) -> bool:
        """保存案例"""
        return await self.case_library.save_case(case)

    # ==================== 短期记忆 ====================

    def set_short_term(self, key: str, value: Any) -> None:
        """设置短期记忆"""
        self._short_term[key] = value

    def get_short_term(self, key: str, default: Any = None) -> Any:
        """获取短期记忆"""
        return self._short_term.get(key, default)

    def clear_short_term(self) -> None:
        """清空短期记忆"""
        self._short_term.clear()

    # ==================== 会话管理 ====================

    def start_session(self, session_id: str) -> None:
        """开始新会话"""
        self.clear_short_term()
        self.set_short_term("session_id", session_id)
        logger.info(f"开始新会话: {session_id}")

    def end_session(self) -> None:
        """结束会话"""
        session_id = self.get_short_term("session_id")
        self.clear_short_term()
        logger.info(f"结束会话: {session_id}")

    # ==================== 统计和清理 ====================

    def stats(self) -> Dict[str, Any]:
        """获取记忆统计"""
        return {
            "knowledge_cache": self.knowledge_cache.stats(),
            "case_library": {
                "local_cases": len(self.case_library.get_local_cases()),
                "success_cases": len(self.case_library.get_success_cases()),
                "failed_cases": len(self.case_library.get_failed_cases()),
            },
            "short_term": len(self._short_term),
        }

    def clear_all(self) -> None:
        """清空所有记忆"""
        self.knowledge_cache.clear()
        self.case_library.clear_local()
        self.clear_short_term()
        logger.info("所有记忆已清空")
