"""
工作流组装器基础抽象类
定义所有组件组装器必须实现的接口
"""

from abc import ABC, abstractmethod
from typing import Dict, Any


class ComponentAssembler(ABC):
    """
    组件组装器基类
    每个组件类型（mysql/hive/datax/sql）都要实现这个接口

    职责：
    1. 验证 LLM 返回的 step 参数是否完整
    2. 将 step 转换为标准 ReactFlow 节点格式
    3. 生成节点显示标签
    """

    @property
    @abstractmethod
    def component_type(self) -> str:
        """
        组件类型标识

        Returns:
            组件类型名称：mysql/hive/datax/sql/flink 等
        """
        pass

    @abstractmethod
    def validate(self, step: Dict[str, Any]) -> bool:
        """
        验证 LLM 返回的 step 是否包含必需参数

        例如：
        - MySQL 需要: table
        - DataX 需要: sourceTable, targetTable
        - SQL 需要: sql

        Args:
            step: LLM 返回的步骤配置

        Returns:
            True 如果参数完整，False 否则
        """
        pass

    @abstractmethod
    def assemble_node(self, step: Dict[str, Any], index: int, node_id: str = None) -> Dict[str, Any]:
        """
        将 step 转换为标准 ReactFlow 节点

        节点格式（参考 xxl_job_workflow.workflow_data）：
        {
            "id": "mysql-1",
            "type": "mysql",
            "position": {"x": 100, "y": 200},
            "data": {
                "label": "订单表",
                "description": "MySQL数据源",
                "componentType": "mysql",
                "status": "idle",
                "forbidden": false,
                ...组件特有配置
            },
            "measured": {"width": 85, "height": 32},
            "selected": false
        }

        Args:
            step: LLM 返回的步骤配置
            index: 节点在工作流中的索引（用于生成ID和位置）
            node_id: 可选的自定义节点ID

        Returns:
            标准 ReactFlow 节点
        """
        pass

    def generate_label(self, step: Dict[str, Any]) -> str:
        """
        生成节点显示标签（子类可选重写）

        默认实现：使用 table 字段或组件类型

        Args:
            step: LLM 返回的步骤配置

        Returns:
            节点显示标签
        """
        return step.get("table") or self.component_type.upper()

    def generate_position(self, index: int, total: int) -> Dict[str, int]:
        """
        生成节点位置（水平布局）

        Args:
            index: 节点索引
            total: 总节点数

        Returns:
            {"x": int, "y": int}
        """
        # 水平间距 250px，垂直居中
        x = 100 + (index + 1) * 250
        y = 200
        return {"x": x, "y": y}
