"""
知识缓存

缓存从 Neo4j 检索的知识，避免重复查询。
"""

import logging
from typing import Dict, Any, Optional, List
from datetime import datetime, timedelta

from src.agent.etl_agents.schemas.context import (
    KnowledgeContext,
    TableSchema,
    JoinHint,
)

logger = logging.getLogger(__name__)


class KnowledgeCache:
    """
    知识缓存

    缓存从 Neo4j 检索到的表结构、血缘、JOIN 关系等信息，
    避免同一会话内重复查询。
    """

    def __init__(self, ttl_minutes: int = 30):
        self._table_cache: Dict[str, Dict[str, Any]] = {}  # {table_name: {data, timestamp}}
        self._lineage_cache: Dict[str, Dict[str, Any]] = {}  # {source->target: {data, timestamp}}
        self._join_cache: Dict[str, Dict[str, Any]] = {}  # {table_name: {data, timestamp}}
        self._dq_cache: Dict[str, Dict[str, Any]] = {}  # {table_name: {data, timestamp}}
        self._ttl = timedelta(minutes=ttl_minutes)

    def _is_expired(self, timestamp: datetime) -> bool:
        """检查缓存是否过期"""
        return datetime.utcnow() - timestamp > self._ttl

    # ==================== 表结构缓存 ====================

    def get_table(self, table_name: str) -> Optional[TableSchema]:
        """获取缓存的表结构"""
        if table_name not in self._table_cache:
            return None

        entry = self._table_cache[table_name]
        if self._is_expired(entry["timestamp"]):
            del self._table_cache[table_name]
            return None

        return entry["data"]

    def set_table(self, table_name: str, table: TableSchema) -> None:
        """缓存表结构"""
        self._table_cache[table_name] = {
            "data": table,
            "timestamp": datetime.utcnow(),
        }

    def get_tables(self, table_names: List[str]) -> Dict[str, TableSchema]:
        """批量获取缓存的表结构"""
        result = {}
        for name in table_names:
            table = self.get_table(name)
            if table:
                result[name] = table
        return result

    # ==================== 血缘缓存 ====================

    def get_lineage(self, source_table: str, target_table: str) -> Optional[Dict[str, Any]]:
        """获取缓存的血缘关系"""
        key = f"{source_table}->{target_table}"
        if key not in self._lineage_cache:
            return None

        entry = self._lineage_cache[key]
        if self._is_expired(entry["timestamp"]):
            del self._lineage_cache[key]
            return None

        return entry["data"]

    def set_lineage(self, source_table: str, target_table: str, lineage: Dict[str, Any]) -> None:
        """缓存血缘关系"""
        key = f"{source_table}->{target_table}"
        self._lineage_cache[key] = {
            "data": lineage,
            "timestamp": datetime.utcnow(),
        }

    # ==================== JOIN 缓存 ====================

    def get_join_hints(self, table_name: str) -> Optional[List[JoinHint]]:
        """获取缓存的 JOIN 关系"""
        if table_name not in self._join_cache:
            return None

        entry = self._join_cache[table_name]
        if self._is_expired(entry["timestamp"]):
            del self._join_cache[table_name]
            return None

        return entry["data"]

    def set_join_hints(self, table_name: str, hints: List[JoinHint]) -> None:
        """缓存 JOIN 关系"""
        self._join_cache[table_name] = {
            "data": hints,
            "timestamp": datetime.utcnow(),
        }

    # ==================== DQ 规则缓存 ====================

    def get_dq_rules(self, table_name: str) -> Optional[List[Dict[str, Any]]]:
        """获取缓存的 DQ 规则"""
        if table_name not in self._dq_cache:
            return None

        entry = self._dq_cache[table_name]
        if self._is_expired(entry["timestamp"]):
            del self._dq_cache[table_name]
            return None

        return entry["data"]

    def set_dq_rules(self, table_name: str, rules: List[Dict[str, Any]]) -> None:
        """缓存 DQ 规则"""
        self._dq_cache[table_name] = {
            "data": rules,
            "timestamp": datetime.utcnow(),
        }

    # ==================== 工具方法 ====================

    def clear(self) -> None:
        """清空所有缓存"""
        self._table_cache.clear()
        self._lineage_cache.clear()
        self._join_cache.clear()
        self._dq_cache.clear()
        logger.info("知识缓存已清空")

    def stats(self) -> Dict[str, int]:
        """获取缓存统计"""
        return {
            "tables": len(self._table_cache),
            "lineage": len(self._lineage_cache),
            "joins": len(self._join_cache),
            "dq_rules": len(self._dq_cache),
        }
