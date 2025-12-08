"""
DataX 组件组装器
"""

from typing import Dict, Any
from ..base import ComponentAssembler


class DataXAssembler(ComponentAssembler):
    """
    DataX 数据同步组装器

    LLM 应返回格式（驼峰命名）：
    {
        "component": "datax",
        "sourceTable": "order",
        "targetTable": "ods_order"
    }

    输出格式（下划线命名，符合前端要求）：
    {
        "id": "datax-1",
        "type": "datax",
        "position": {"x": 350, "y": 200},
        "data": {
            "label": "MySQL到Hive同步",
            "source_table": "order",
            "target_table": "ods_order",
            "status": "idle",
            "forbidden": false
        }
    }
    """

    @property
    def component_type(self) -> str:
        return "datax"

    def validate(self, step: Dict[str, Any]) -> bool:
        """
        验证必需参数

        必需：
        - sourceTable: 源表（LLM返回的驼峰命名）
        - targetTable: 目标表
        """
        return "sourceTable" in step and "targetTable" in step

    def assemble_node(self, step: Dict[str, Any], index: int, node_id: str = None) -> Dict[str, Any]:
        """
        组装 DataX 节点

        Args:
            step: LLM 返回的配置（驼峰命名）
            index: 节点索引
            node_id: 可选的自定义节点ID

        Returns:
            标准 ReactFlow 节点（下划线命名）
        """
        # 生成节点ID
        if node_id is None:
            node_id = f"datax-{index + 1}"

        # 生成显示标签
        source_table = step.get("sourceTable", "")
        target_table = step.get("targetTable", "")
        label = f"{source_table} → {target_table}"

        # 生成节点位置
        position = self.generate_position(index, 0)

        # 组装节点（转换为下划线命名）
        return {
            "id": node_id,
            "type": "datax",
            "position": position,
            "data": {
                "label": label,
                "source_table": source_table,  # 转换为下划线命名
                "target_table": target_table,
                "status": "idle",
                "forbidden": False,
            },
            "measured": {"width": 120, "height": 32},
            "selected": False
        }

    def generate_label(self, step: Dict[str, Any]) -> str:
        """生成节点显示标签"""
        source = step.get("sourceTable", "")
        target = step.get("targetTable", "")
        return f"{source} → {target}"
