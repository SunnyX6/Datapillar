"""
Neo4j 列查询服务

职责：提供列相关的查询功能（预留给知识图谱使用）

说明：
- Agent 场景下，列信息通过 search_table.get_table_detail 获取
- 知识图谱如有单独查列的需求，在此文件扩展
"""

from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


class Neo4jColumnSearch:
    """Neo4j 列查询服务（预留）"""

    pass
