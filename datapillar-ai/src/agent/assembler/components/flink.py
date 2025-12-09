"""
Flink 组件组装器
"""

import re
from typing import Dict, Any
from ..base import ComponentAssembler


class FlinkAssembler(ComponentAssembler):
    """
    Flink SQL 执行组装器

    LLM 应返回格式：
    {
        "component": "flink",
        "sql": "SELECT * FROM order_stream WHERE order_amount > 1000"
    }

    输出格式：
    {
        "id": "flink-1",
        "type": "flink",
        "position": {"x": 350, "y": 200},
        "data": {
            "label": "Flink: 实时查询",
            "sql": "SELECT * FROM...",
            "status": "idle",
            "forbidden": false
        }
    }
    """

    @property
    def component_type(self) -> str:
        return "flink"

    def validate(self, step: Dict[str, Any]) -> bool:
        """
        验证必需参数

        必需：
        - sql: Flink SQL 语句
        """
        return "sql" in step and step["sql"].strip()

    def assemble_node(self, step: Dict[str, Any], index: int, node_id: str = None) -> Dict[str, Any]:
        """
        组装 Flink 节点

        Args:
            step: LLM 返回的配置
            index: 节点索引
            node_id: 可选的自定义节点ID

        Returns:
            标准 ReactFlow 节点
        """
        # 生成节点ID
        if node_id is None:
            node_id = f"flink-{index + 1}"

        sql = step.get("sql", "").strip()

        # 生成显示标签
        label = self._generate_label_from_sql(sql)

        # 生成节点位置
        position = self.generate_position(index, 0)

        # 组装节点
        return {
            "id": node_id,
            "type": "flink",
            "position": position,
            "data": {
                "label": label,
                "sql": sql,
                "status": "idle",
                "forbidden": False,
            },
            "measured": {"width": 85, "height": 32},
            "selected": False
        }

    def generate_label(self, step: Dict[str, Any]) -> str:
        """生成节点显示标签"""
        sql = step.get("sql", "")
        return self._generate_label_from_sql(sql)

    def _generate_label_from_sql(self, sql: str) -> str:
        """
        从 SQL 中生成简洁的标签

        优先提取表名，否则使用通用标签
        """
        # 移除多余空格和换行
        sql = " ".join(sql.split())

        # 尝试提取表名
        match = re.search(r"FROM\s+(\w+)", sql, re.IGNORECASE)
        if match:
            table_name = match.group(1)
            return f"Flink: {table_name}"

        # 如果没有 FROM，返回通用标签
        return "Flink: 实时查询"
