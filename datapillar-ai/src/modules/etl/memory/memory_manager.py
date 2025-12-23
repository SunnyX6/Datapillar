"""
记忆管理器

统一管理知识缓存和短期记忆。
"""

import logging
from typing import Dict, Any, Optional, List

from src.modules.etl.memory.knowledge_cache import KnowledgeCache

logger = logging.getLogger(__name__)


class MemoryManager:
    """
    记忆管理器

    统一管理：
    - KnowledgeCache: 知识缓存（表结构、血缘）
    - ShortTermMemory: 短期记忆（当前会话状态）
    """

    def __init__(self, cache_ttl_minutes: int = 30):
        self.knowledge_cache = KnowledgeCache(ttl_minutes=cache_ttl_minutes)

        # 短期记忆（当前会话）
        self._short_term: Dict[str, Any] = {}

    # ==================== 知识缓存代理方法 ====================

    def get_cached_table(self, table_name: str) -> Optional[Dict[str, Any]]:
        """获取缓存的表结构"""
        return self.knowledge_cache.get_table(table_name)

    def cache_table(self, table_name: str, table: Dict[str, Any]) -> None:
        """缓存表结构"""
        self.knowledge_cache.set_table(table_name, table)

    def get_cached_tables(self, table_names: List[str]) -> Dict[str, Dict[str, Any]]:
        """批量获取缓存的表结构"""
        return self.knowledge_cache.get_tables(table_names)

    def cache_tables(self, tables: Dict[str, Dict[str, Any]]) -> None:
        """批量缓存表结构"""
        for name, table in tables.items():
            self.knowledge_cache.set_table(name, table)

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
            "short_term": len(self._short_term),
        }

    def clear_all(self) -> None:
        """清空所有记忆"""
        self.knowledge_cache.clear()
        self.clear_short_term()
        logger.info("所有记忆已清空")
