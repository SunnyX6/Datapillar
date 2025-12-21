"""
DAG 渲染数据结构（React Flow 适配层）

将统一的 Workflow + Jobs 格式转换为前端 React Flow 渲染格式。
前端业务层使用 Workflow + Jobs，渲染层使用 ReactFlowDag。
"""

from typing import List, Dict, Any, Optional, Literal
from pydantic import BaseModel, Field

from src.modules.etl.schemas.plan import Workflow, Job


class NodePosition(BaseModel):
    """节点位置"""
    x: float
    y: float


class NodeData(BaseModel):
    """节点数据"""
    label: str
    description: Optional[str] = None
    nodeType: Literal["source", "transform", "sink", "dq"] = "transform"
    component_id: Optional[str] = None
    sql: Optional[str] = None
    sqlValidated: bool = False
    params: Dict[str, Any] = Field(default_factory=dict)


class DagNode(BaseModel):
    """DAG 节点（React Flow 格式）"""
    id: str
    type: str
    data: NodeData
    position: NodePosition


class DagEdge(BaseModel):
    """DAG 边（React Flow 格式）"""
    id: str
    source: str
    target: str
    animated: bool = False
    style: Dict[str, Any] = Field(default_factory=dict)


class DagMetadata(BaseModel):
    """DAG 元数据"""
    name: str
    description: Optional[str] = None
    layers: List[str] = Field(default_factory=list)
    env: str = "dev"
    schedule: Optional[str] = None
    risks: List[str] = Field(default_factory=list)
    confidence: float = 0.5


class ReactFlowDag(BaseModel):
    """
    React Flow DAG 格式

    可直接用于前端 React Flow 渲染。
    """
    metadata: DagMetadata
    nodes: List[DagNode] = Field(default_factory=list)
    edges: List[DagEdge] = Field(default_factory=list)

    @property
    def node_count(self) -> int:
        return len(self.nodes)

    @property
    def edge_count(self) -> int:
        return len(self.edges)

    def summary(self) -> str:
        """生成摘要"""
        return f"{self.metadata.name}，共 {self.node_count} 个节点、{self.edge_count} 条连线"


class DagLayoutEngine:
    """
    DAG 布局引擎

    基于拓扑排序的分层布局算法。
    """

    def __init__(
        self,
        node_width: float = 200,
        node_height: float = 80,
        horizontal_gap: float = 100,
        vertical_gap: float = 50,
    ):
        self.node_width = node_width
        self.node_height = node_height
        self.horizontal_gap = horizontal_gap
        self.vertical_gap = vertical_gap

    def calculate_positions(
        self,
        jobs: List[Job],
    ) -> Dict[str, NodePosition]:
        """
        计算节点位置

        使用分层布局：
        1. 根据依赖关系分层
        2. 同层节点垂直排列
        3. 层与层之间水平排列
        """
        if not jobs:
            return {}

        job_map = {j.id: j for j in jobs}
        in_degree = {j.id: 0 for j in jobs}
        out_edges = {j.id: [] for j in jobs}

        for job in jobs:
            for dep_id in job.depends:
                if dep_id in job_map:
                    out_edges[dep_id].append(job.id)
                    in_degree[job.id] += 1

        layers: List[List[str]] = []
        remaining = set(job_map.keys())

        while remaining:
            current_layer = [
                jid for jid in remaining
                if in_degree[jid] == 0
            ]

            if not current_layer:
                current_layer = [list(remaining)[0]]

            layers.append(current_layer)

            for jid in current_layer:
                remaining.remove(jid)
                for out_jid in out_edges[jid]:
                    if out_jid in remaining:
                        in_degree[out_jid] -= 1

        positions = {}
        x = 50

        for layer_idx, layer in enumerate(layers):
            total_height = len(layer) * self.node_height + (len(layer) - 1) * self.vertical_gap
            start_y = 200 - total_height / 2

            for job_idx, job_id in enumerate(layer):
                y = start_y + job_idx * (self.node_height + self.vertical_gap)
                positions[job_id] = NodePosition(x=x, y=y)

            x += self.node_width + self.horizontal_gap

        return positions


def workflow_to_react_flow(workflow: Workflow) -> ReactFlowDag:
    """
    将 Workflow 转换为 React Flow DAG 格式（前端渲染层）
    """
    metadata = DagMetadata(
        name=workflow.name,
        description=workflow.description,
        layers=workflow.layers,
        env=workflow.env,
        schedule=workflow.schedule,
        risks=workflow.risks,
        confidence=workflow.confidence,
    )

    layout_engine = DagLayoutEngine()
    positions = layout_engine.calculate_positions(workflow.jobs)

    dag_nodes: List[DagNode] = []
    for job in workflow.jobs:
        position = positions.get(job.id, NodePosition(x=0, y=0))

        sql_content = job.config.get("sql") if job.config else None

        dag_node = DagNode(
            id=job.id,
            type="etlNode",
            data=NodeData(
                label=job.name or job.id,
                description=job.description,
                nodeType="transform",
                component_id=job.type,  # 统一字段：type
                sql=sql_content,
                sqlValidated=job.config_validated,
                params=job.config,
            ),
            position=position,
        )
        dag_nodes.append(dag_node)

    dag_edges: List[DagEdge] = []
    edge_id = 0

    for job in workflow.jobs:
        for dep_id in job.depends:  # 统一字段：depends
            edge_id += 1
            dag_edge = DagEdge(
                id=f"e{edge_id}",
                source=dep_id,
                target=job.id,
                animated=False,
                style={"stroke": "#b1b1b7"},
            )
            dag_edges.append(dag_edge)

    return ReactFlowDag(
        metadata=metadata,
        nodes=dag_nodes,
        edges=dag_edges,
    )
