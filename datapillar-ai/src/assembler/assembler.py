"""
主工作流组装器
负责将 LLM 输出转换为标准 ReactFlow workflow_data 格式
"""

from typing import Dict, Any, List
import logging

logger = logging.getLogger(__name__)
from .registry import ComponentRegistry


class WorkflowAssembler:
    """
    主工作流组装器

    职责：
    1. 将 LLM 的简化输出转换为标准 ReactFlow 格式
    2. 添加 start/end 节点
    3. 生成节点连接边
    4. 生成 viewport 配置

    输入格式（LLM 返回）：
    {
        "taskType": "数据清洗",
        "description": "清洗订单数据",
        "steps": [
            {"component": "mysql", "table": "order"},
            {"component": "sql", "sql": "SELECT * FROM upstream WHERE amount > 0"},
            {"component": "hive", "table": "order_cleaned", "mode": "overwrite"}
        ]
    }

    输出格式（标准 ReactFlow）：
    {
        "nodes": [...],
        "edges": [...],
        "viewport": {"x": 0, "y": 0, "zoom": 1}
    }
    """

    def __init__(self):
        # 确保组件已注册（导入 components 模块会自动注册）
        from . import components  # noqa: F401

        self.registry = ComponentRegistry.get_instance()

    def assemble(self, llm_output: Dict[str, Any]) -> Dict[str, Any]:
        """
        将 LLM 输出组装为标准 workflow_data

        Args:
            llm_output: LLM 返回的工作流定义

        Returns:
            标准 ReactFlow workflow_data

        Raises:
            ValueError: 如果参数不完整或组件类型未注册
        """
        logger.info(f"开始组装工作流: {llm_output.get('taskType', '未命名')}")

        # 验证输入
        if "steps" not in llm_output or not llm_output["steps"]:
            raise ValueError("LLM 输出缺少 steps 字段")

        steps = llm_output["steps"]

        nodes = []
        edges = []

        # 1. 创建 start 节点
        start_node = self._create_start_node()
        nodes.append(start_node)

        # 2. 处理每个 step，转换为节点
        for i, step in enumerate(steps):
            component_type = step.get("component")

            if not component_type:
                raise ValueError(f"步骤 {i} 缺少 component 字段: {step}")

            # 获取对应的组装器
            try:
                assembler = self.registry.get(component_type)
            except ValueError as e:
                logger.error(f"未找到组件 {component_type} 的组装器")
                raise e

            # 验证参数
            if not assembler.validate(step):
                raise ValueError(f"步骤 {i} 参数验证失败: {step}")

            # 组装节点
            node = assembler.assemble_node(step, i)
            nodes.append(node)

            logger.info(f"组装节点 {i}: {node['id']} ({component_type})")

        # 3. 创建 end 节点
        end_node = self._create_end_node(len(steps))
        nodes.append(end_node)

        # 4. 生成连接边（start -> step1 -> step2 -> ... -> end）
        edges = self._create_edges(nodes)

        # 5. 生成 viewport
        viewport = self._create_viewport()

        workflow_data = {
            "nodes": nodes,
            "edges": edges,
            "viewport": viewport
        }

        logger.info(f"✅ 工作流组装完成: {len(nodes)} 个节点, {len(edges)} 条边")

        return workflow_data

    def _create_start_node(self) -> Dict[str, Any]:
        """
        创建开始节点

        Returns:
            标准 start 节点
        """
        return {
            "id": "start-1",
            "type": "start",
            "position": {"x": 100, "y": 200},
            "data": {
                "label": "开始",
                "status": "idle",
                "description": "工作流开始节点",
                "forbidden": False
            },
            "measured": {"width": 77, "height": 32},
            "selected": False
        }

    def _create_end_node(self, step_count: int) -> Dict[str, Any]:
        """
        创建结束节点

        Args:
            step_count: 步骤数量（用于计算位置）

        Returns:
            标准 end 节点
        """
        # 结束节点位置：在最后一个 step 之后
        x = 100 + (step_count + 1) * 250
        y = 200

        return {
            "id": "end-1",
            "type": "end",
            "position": {"x": x, "y": y},
            "data": {
                "label": "结束",
                "description": "工作流结束节点",
                "outputType": "success",
                "outputMessage": "工作流执行完成",
                "saveResults": True,
                "status": "idle",
                "forbidden": False
            },
            "measured": {"width": 77, "height": 32},
            "selected": False
        }

    def _create_edges(self, nodes: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        生成节点连接边（线性流程）

        Args:
            nodes: 所有节点列表

        Returns:
            边列表
        """
        edges = []

        for i in range(len(nodes) - 1):
            source_id = nodes[i]["id"]
            target_id = nodes[i + 1]["id"]

            edge = {
                "id": f"edge-{i}",
                "source": source_id,
                "target": target_id,
                "sourceHandle": "output",
                "targetHandle": "input",
                "type": "bezier",
                "animated": False,
                "reconnectable": True,
                "selected": False
            }

            edges.append(edge)

        return edges

    def _create_viewport(self) -> Dict[str, Any]:
        """
        创建 viewport 配置

        Returns:
            viewport 配置
        """
        return {
            "x": 0,
            "y": 0,
            "zoom": 1
        }
