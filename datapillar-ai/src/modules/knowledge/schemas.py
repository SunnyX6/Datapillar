# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识图谱请求/响应模型
"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class KGEventType(str, Enum):
    """知识图谱 SSE 事件类型"""

    STREAM_START = "stream_start"
    NODES_BATCH = "nodes_batch"
    RELS_BATCH = "rels_batch"
    SEARCH_RESULT = "search_result"
    STREAM_END = "stream_end"
    ERROR = "error"


# 节点类型层级映射（按包含关系从内到外）
NODE_TYPE_LEVELS: dict[str, int] = {
    # Level 0: 业务域（最内层）
    "Domain": 0,
    # Level 1: 数据目录
    "Catalog": 1,
    # Level 2: 业务主题（Catalog 包含 Subject）
    "Subject": 2,
    # Level 3: 数据分层（Schema 的 layer 属性区分 SRC/ODS/DWD/DWS/ADS）
    "Schema": 3,
    # Level 4: 表
    "Table": 4,
    # Level 5: 列
    "Column": 5,
    # Level 6: 指标
    "AtomicMetric": 6,
    "DerivedMetric": 6,
    "CompositeMetric": 6,
    # Level 7: 质量规则
    "QualityRule": 7,
}

# 默认层级（未知类型）
DEFAULT_NODE_LEVEL = 99


def get_node_level(node_type: str) -> int:
    """根据节点类型获取层级"""
    return NODE_TYPE_LEVELS.get(node_type, DEFAULT_NODE_LEVEL)


class KGStreamEvent(BaseModel):
    """知识图谱 SSE 事件"""

    event_type: KGEventType
    data: str = Field(description="msgpack 编码后 base64 字符串")
    total: int | None = Field(default=None, description="总数")
    current: int | None = Field(default=None, description="当前批次")


class GraphNode(BaseModel):
    """图节点"""

    id: int
    type: str = Field(description="节点类型，如 Table、Column、Metric 等")
    level: int = Field(default=DEFAULT_NODE_LEVEL, description="层级，用于分层布局")
    properties: dict[str, Any]


class GraphRelationship(BaseModel):
    """图关系"""

    id: int
    start: int
    end: int
    type: str
    properties: dict[str, Any]


class GraphData(BaseModel):
    """图数据"""

    nodes: list[GraphNode] = Field(default_factory=list)
    relationships: list[GraphRelationship] = Field(default_factory=list)


class GraphSearchRequest(BaseModel):
    """图搜索请求"""

    query: str = Field(..., description="自然语言查询")
    top_k: int = Field(default=10, ge=1, le=100, description="返回结果数量")


class GraphSearchResult(BaseModel):
    """图搜索结果"""

    nodes: list[GraphNode] = Field(default_factory=list)
    relationships: list[GraphRelationship] = Field(default_factory=list)
    highlight_node_ids: list[int] = Field(default_factory=list, description="高亮节点ID")
