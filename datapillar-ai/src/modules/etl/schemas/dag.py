"""
工作流输出数据结构

将 Workflow 转换为与 Web-Admin 兼容的格式，前端可直接用于保存。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field

from src.modules.etl.schemas.plan import Job, Workflow


class JobResponse(BaseModel):
    """任务响应格式（与 Web-Admin JobDto.Response 兼容）"""

    id: int | None = Field(default=None, description="任务 ID（临时 ID，保存时由后端生成）")
    jobName: str = Field(..., description="任务名称")
    jobType: int | None = Field(default=None, description="任务类型 ID")
    jobTypeCode: str = Field(..., description="组件代码（hive/shell/python 等）")
    jobTypeName: str | None = Field(default=None, description="组件名称")
    jobParams: dict[str, Any] = Field(default_factory=dict, description="任务参数")
    timeoutSeconds: int = Field(default=0, description="超时时间（秒）")
    maxRetryTimes: int = Field(default=0, description="最大重试次数")
    retryInterval: int = Field(default=0, description="重试间隔（秒）")
    priority: int = Field(default=0, description="优先级")
    positionX: float = Field(default=0, description="X 坐标")
    positionY: float = Field(default=0, description="Y 坐标")
    description: str | None = Field(default=None, description="任务描述")


class JobDependencyResponse(BaseModel):
    """任务依赖响应格式（与 Web-Admin JobDependencyDto.Response 兼容）"""

    jobId: int = Field(..., description="当前任务 ID")
    parentJobId: int = Field(..., description="上游任务 ID")


class WorkflowResponse(BaseModel):
    """
    工作流响应格式（与 Web-Admin WorkflowDto.Response 兼容）

    前端可直接用于：
    1. 渲染 DAG 画布（jobs 的 positionX/Y）
    2. 保存到后端（调用 Web-Admin API）
    """

    workflowName: str = Field(..., description="工作流名称")
    triggerType: int = Field(
        default=4, description="触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API"
    )
    triggerValue: str | None = Field(default=None, description="触发配置（CRON 表达式或秒数）")
    timeoutSeconds: int = Field(default=0, description="超时时间（秒）")
    maxRetryTimes: int = Field(default=0, description="最大重试次数")
    priority: int = Field(default=0, description="优先级")
    description: str | None = Field(default=None, description="工作流描述")
    jobs: list[JobResponse] = Field(default_factory=list, description="任务列表")
    dependencies: list[JobDependencyResponse] = Field(
        default_factory=list, description="依赖关系列表"
    )

    @classmethod
    def from_workflow(cls, workflow: Workflow) -> WorkflowResponse:
        """
        从 Workflow 转换为 Web-Admin 兼容的响应格式

        Args:
            workflow: ETL Agent 生成的 Workflow

        Returns:
            WorkflowResponse: 可直接用于前端渲染和保存的格式
        """
        # 计算布局
        layout_engine = DagLayoutEngine()
        positions = layout_engine.calculate_positions(workflow.jobs)

        # 解析 schedule 为 triggerType 和 triggerValue
        trigger_type = 4  # 默认手动
        trigger_value = None
        if workflow.schedule:
            trigger_type = 1  # CRON
            trigger_value = workflow.schedule

        # 建立字符串 ID 到数字临时 ID 的映射
        id_mapping: dict[str, int] = {}
        for idx, job in enumerate(workflow.jobs, start=1):
            id_mapping[job.id] = idx

        # 转换 Jobs
        job_responses: list[JobResponse] = []
        for job in workflow.jobs:
            pos_x, pos_y = positions.get(job.id, (0.0, 0.0))
            temp_id = id_mapping.get(job.id)

            job_response = JobResponse(
                id=temp_id,
                jobName=job.name or job.id,
                jobType=job.type_id,
                jobTypeCode=job.type,
                jobParams=job.config or {},
                timeoutSeconds=job.timeout or 0,
                maxRetryTimes=job.retry_times or 0,
                retryInterval=0,
                priority=job.priority or 0,
                positionX=pos_x,
                positionY=pos_y,
                description=job.description,
            )
            job_responses.append(job_response)

        # 转换 Dependencies
        dependency_responses: list[JobDependencyResponse] = []
        for job in workflow.jobs:
            for parent_id in job.depends:
                job_temp_id = id_mapping.get(job.id, 0)
                parent_temp_id = id_mapping.get(parent_id, 0)
                dependency_response = JobDependencyResponse(
                    jobId=job_temp_id,
                    parentJobId=parent_temp_id,
                )
                dependency_responses.append(dependency_response)

        return cls(
            workflowName=workflow.name,
            triggerType=trigger_type,
            triggerValue=trigger_value,
            timeoutSeconds=0,
            maxRetryTimes=0,
            priority=0,
            description=workflow.description,
            jobs=job_responses,
            dependencies=dependency_responses,
        )


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

    def calculate_positions(self, jobs: list[Job]) -> dict[str, tuple[float, float]]:
        """
        计算节点位置

        使用分层布局：
        1. 根据依赖关系分层
        2. 同层节点垂直排列
        3. 层与层之间水平排列

        返回: {job_id: (x, y)}
        """
        if not jobs:
            return {}

        job_map = {j.id: j for j in jobs}
        in_degree = {j.id: 0 for j in jobs}
        out_edges: dict[str, list[str]] = {j.id: [] for j in jobs}

        for job in jobs:
            for dep_id in job.depends:
                if dep_id in job_map:
                    out_edges[dep_id].append(job.id)
                    in_degree[job.id] += 1

        layers: list[list[str]] = []
        remaining = set(job_map.keys())

        while remaining:
            current_layer = [jid for jid in remaining if in_degree[jid] == 0]

            if not current_layer:
                current_layer = [list(remaining)[0]]

            layers.append(current_layer)

            for jid in current_layer:
                remaining.remove(jid)
                for out_jid in out_edges[jid]:
                    if out_jid in remaining:
                        in_degree[out_jid] -= 1

        positions: dict[str, tuple[float, float]] = {}
        x = 50.0

        for layer in layers:
            total_height = len(layer) * self.node_height + (len(layer) - 1) * self.vertical_gap
            start_y = 200 - total_height / 2

            for job_idx, job_id in enumerate(layer):
                y = start_y + job_idx * (self.node_height + self.vertical_gap)
                positions[job_id] = (x, y)

            x += self.node_width + self.horizontal_gap

        return positions
