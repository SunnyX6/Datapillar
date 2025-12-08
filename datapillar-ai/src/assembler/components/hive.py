"""
Hive 组件组装器
"""

import re
from typing import Dict, Any
from ..base import ComponentAssembler


class HiveAssembler(ComponentAssembler):
    """
    Hive SQL 执行组装器

    LLM 应返回格式：
    {
        "component": "hive",
        "sql": "INSERT INTO order_cleaned SELECT * FROM order WHERE amount > 0"
    }

    输出格式：
    {
        "id": "hive-1",
        "type": "hive",
        "position": {"x": 350, "y": 200},
        "data": {
            "label": "Hive查询",
            "sql": "INSERT INTO...",
            "source_table": "order",  # 从SQL中提取
            "target_table": "order_cleaned",  # 从SQL中提取
            "status": "idle",
            "forbidden": false
        }
    }
    """

    @property
    def component_type(self) -> str:
        return "hive"

    def validate(self, step: Dict[str, Any]) -> bool:
        """
        验证必需参数

        必需：
        - sql: Hive SQL 语句
        """
        return "sql" in step and step["sql"].strip()

    def assemble_node(self, step: Dict[str, Any], index: int, node_id: str = None) -> Dict[str, Any]:
        """
        组装 Hive 节点

        Args:
            step: LLM 返回的配置
            index: 节点索引
            node_id: 可选的自定义节点ID

        Returns:
            标准 ReactFlow 节点
        """
        # 生成节点ID
        if node_id is None:
            node_id = f"hive-{index + 1}"

        sql = step.get("sql", "").strip()

        # 从 SQL 中提取表名
        source_table = self._extract_source_table(sql)
        target_table = self._extract_target_table(sql)

        # 生成显示标签
        label = "Hive查询"
        if target_table:
            label = f"Hive: {target_table}"

        # 生成节点位置
        position = self.generate_position(index, 0)

        # 组装节点
        return {
            "id": node_id,
            "type": "hive",
            "position": position,
            "data": {
                "label": label,
                "sql": sql,
                "source_table": source_table,
                "target_table": target_table,
                "status": "idle",
                "forbidden": False,
            },
            "measured": {"width": 85, "height": 32},
            "selected": False
        }

    def generate_label(self, step: Dict[str, Any]) -> str:
        """生成节点显示标签"""
        sql = step.get("sql", "")
        target_table = self._extract_target_table(sql)
        return f"Hive: {target_table}" if target_table else "Hive查询"

    def _extract_target_table(self, sql: str) -> str:
        """
        从 SQL 中提取目标表名

        支持格式：
        - INSERT INTO table_name ...
        - INSERT OVERWRITE TABLE table_name ...
        """
        # 移除多余空格和换行
        sql = " ".join(sql.split())

        # 匹配 INSERT INTO table_name
        match = re.search(r"INSERT\s+INTO\s+(\w+)", sql, re.IGNORECASE)
        if match:
            return match.group(1)

        # 匹配 INSERT OVERWRITE TABLE table_name
        match = re.search(r"INSERT\s+OVERWRITE\s+TABLE\s+(\w+)", sql, re.IGNORECASE)
        if match:
            return match.group(1)

        return ""

    def _extract_source_table(self, sql: str) -> str:
        """
        从 SQL 中提取源表名

        支持格式：
        - SELECT ... FROM table_name ...
        """
        # 移除多余空格和换行
        sql = " ".join(sql.split())

        # 匹配 FROM table_name
        match = re.search(r"FROM\s+(\w+)", sql, re.IGNORECASE)
        if match:
            return match.group(1)

        return ""
