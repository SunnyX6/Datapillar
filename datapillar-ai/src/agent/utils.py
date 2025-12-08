"""
Agent 通用工具函数

抽取 planner_agent 和 coder_agent 中的共性工具函数
"""

import json
import re
from typing import Dict, Any, List, Set
import logging

logger = logging.getLogger(__name__)


def extract_json_from_text(content: str) -> str:
    """
    从 LLM 文本输出中提取 JSON 字符串

    优先解析 ```json``` 代码块，否则提取 { } 包裹的内容

    Args:
        content: LLM 返回的文本内容

    Returns:
        提取的 JSON 字符串

    Raises:
        ValueError: 未找到合法的 JSON
    """
    if not content:
        raise ValueError("LLM 未返回任何内容，无法提取 JSON")

    # 优先解析 ```json``` 代码块
    code_blocks = re.findall(r"```(?:json)?\s*(.*?)```", content, re.DOTALL)
    if code_blocks:
        return code_blocks[-1].strip()

    # 尝试提取裸 JSON
    content = content.strip()
    start = content.find("{")
    end = content.rfind("}")
    if start != -1 and end != -1 and end > start:
        return content[start: end + 1]

    raise ValueError("未找到合法的 JSON，请确保 LLM 输出包含 JSON 格式")


def validate_dag(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> None:
    """
    验证 DAG 无环（拓扑排序验证）

    Args:
        nodes: 节点列表，每个节点必须包含 "id" 字段
        edges: 边列表，每个边必须包含 "source" 和 "target" 字段

    Raises:
        ValueError: 如果检测到循环依赖或边引用了不存在的节点
    """
    # 构建节点ID集合
    node_ids: Set[str] = {node["id"] for node in nodes}

    # 验证边的 source/target 必须在 nodes 中存在
    for edge in edges:
        source = edge.get("source")
        target = edge.get("target")
        if source not in node_ids:
            raise ValueError(f"边 {edge.get('id')} 的 source '{source}' 不在节点列表中")
        if target not in node_ids:
            raise ValueError(f"边 {edge.get('id')} 的 target '{target}' 不在节点列表中")

    # 如果没有边，直接返回（并行任务）
    if not edges:
        return

    # 构建邻接表和入度表
    graph: Dict[str, List[str]] = {node_id: [] for node_id in node_ids}
    in_degree: Dict[str, int] = {node_id: 0 for node_id in node_ids}

    for edge in edges:
        source = edge["source"]
        target = edge["target"]
        graph[source].append(target)
        in_degree[target] += 1

    # Kahn 算法拓扑排序
    queue = [node_id for node_id, degree in in_degree.items() if degree == 0]
    sorted_count = 0

    while queue:
        current = queue.pop(0)
        sorted_count += 1

        for neighbor in graph[current]:
            in_degree[neighbor] -= 1
            if in_degree[neighbor] == 0:
                queue.append(neighbor)

    # 如果排序的节点数量少于总节点数，说明有环
    if sorted_count < len(node_ids):
        raise ValueError("检测到循环依赖！工作流的 edges 必须是 DAG（无环有向图）")

    logger.info(f"✅ DAG 验证通过：{len(nodes)} 个节点，{len(edges)} 条边，无循环依赖")
