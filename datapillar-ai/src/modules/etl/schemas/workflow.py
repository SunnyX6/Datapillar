# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
工作流数据结构（统一三端：AI/前端/调度）

层级关系：
- Workflow：工作流，由多个 Job 组成的 DAG
- Job：作业/任务，每个 Job 是一个独立的执行单元（前端一个节点）
- Stage：SQL 执行单元，一个 Job 可包含多个 Stage（架构师规划）

术语：
- type：Job 类型（hive/shell/datax/flink 等）
- depends：Job 之间的依赖关系
"""

import json
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


def _try_parse_json(value: object) -> object:
    if not isinstance(value, str):
        return value
    text = value.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return value


# ==================== LLM 输出专用 Schema ====================
# 只包含 LLM 需要输出的字段，其他字段由代码填充


class StageOutput(BaseModel):
    """Stage 输出（LLM 生成，用于 structured output）"""

    stage_id: int = Field(..., description="Stage 序号，Job 内唯一，从 1 开始递增")
    name: str = Field(..., description="Stage 名称，简洁描述这个阶段做什么")
    description: str = Field(..., description="Stage 详细描述，说明数据处理逻辑")
    input_tables: list[str] = Field(
        default_factory=list,
        description="读取的表列表，持久表为 catalog.schema.table，临时表允许 temp.xxx",
    )
    output_table: str = Field(
        ...,
        description="输出表名，持久表为 catalog.schema.table，临时表允许 temp.xxx",
    )
    is_temp_table: bool = Field(default=True, description="是否是临时表，临时表只在当前 Job 内有效")
    sql: str | None = Field(default=None, description="SQL 语句（开发阶段补充）")

    @field_validator("input_tables", mode="before")
    @classmethod
    def _parse_input_tables(cls, v: object) -> object:
        return _try_parse_json(v)


class JobOutput(BaseModel):
    """Job 输出（LLM 生成，用于 structured output）"""

    id: str = Field(..., description="Job 唯一标识，建议使用 job_1, job_2 格式")
    name: str = Field(..., description="Job 名称，简洁描述这个作业做什么")
    description: str | None = Field(None, description="Job 详细描述")
    depends: list[str] = Field(
        default_factory=list,
        description="依赖的上游 Job ID 列表，用于调度依赖，如果 Job B 读的表是 Job A 写的，则填 Job A 的 ID",
    )
    job_ids: list[str] = Field(
        default_factory=list,
        description="关联的业务 Job ID 列表，来自 AnalysisResult.pipelines[].jobs",
    )
    stages: list[StageOutput] = Field(
        default_factory=list, description="执行阶段列表，按执行顺序排列"
    )
    input_tables: list[str] = Field(default_factory=list, description="Job 读取的持久化表列表")
    output_table: str | None = Field(None, description="Job 写入的最终目标表")

    @field_validator("depends", "job_ids", "input_tables", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        """容错：null -> 空列表，字符串化 JSON -> 解析"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v

    @field_validator("stages", mode="before")
    @classmethod
    def _parse_stages(cls, v: object) -> object:
        return _try_parse_json(v)


class WorkflowOutput(BaseModel):
    """
    工作流输出（LLM 生成，用于 structured output）

    不含 id/env 等运行时字段，由代码填充。
    """

    name: str = Field(..., description="工作流名称，简洁描述整个 ETL 流程")
    description: str | None = Field(None, description="工作流详细描述")
    schedule: str | None = Field(..., description="调度周期 cron 表达式，未明确则为 null")
    jobs: list[JobOutput] = Field(default_factory=list, description="作业列表，按 DAG 拓扑顺序排列")
    risks: list[str] = Field(
        default_factory=list, description="架构风险点列表，如性能瓶颈、数据倾斜等"
    )
    confidence: float = Field(
        default=0.8, ge=0.0, le=1.0, description="架构方案置信度，复杂场景应 < 0.8"
    )

    @field_validator("jobs", mode="before")
    @classmethod
    def _parse_jobs(cls, v: object) -> object:
        return _try_parse_json(v)

    @field_validator("risks", mode="before")
    @classmethod
    def _parse_risks(cls, v: object) -> object:
        """容错：null -> 空列表，字符串化 JSON -> 解析"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


# ==================== 完整数据结构 ====================


class Stage(BaseModel):
    """
    Stage - SQL 执行单元

    由架构师规划，一个 Stage 对应一个 SQL，产出一个表。
    一个 Job 可包含多个 Stage，按顺序执行。
    """

    stage_id: int = Field(..., description="Stage 序号（Job 内唯一）")
    name: str = Field(..., description="Stage 名称")
    description: str = Field(..., description="这个 Stage 做什么")

    input_tables: list[str] = Field(default_factory=list, description="读取的表（持久表或临时表）")
    output_table: str = Field(..., description="输出表（持久表或临时表）")
    is_temp_table: bool = Field(default=True, description="是否是临时表")

    sql: str | None = Field(None, description="SQL 语句（由 DeveloperAgent 生成）")


class Job(BaseModel):
    """
    作业定义（统一三端命名：Job）

    每个 Job 是一个独立的执行单元（前端一个节点）。
    一个 Job 可包含多个业务 Job（job_ids）和多个执行阶段（stages）。
    """

    # 基础信息
    id: str = Field(..., description="Job 唯一标识")
    name: str = Field(..., description="Job 名称（中文）")
    description: str | None = Field(None, description="Job 描述")

    # 作业类型（统一字段：type）
    type: str = Field(..., description="Job 类型：HIVE/SHELL/DATAX/SPARK_SQL/PYTHON")
    type_id: int | None = Field(None, description="组件数字 ID（对应 job_component.id）")

    # 依赖关系（统一字段：depends）
    depends: list[str] = Field(default_factory=list, description="依赖的上游 Job ID 列表")

    # 关联的业务 Job（来自 AnalystAgent）
    job_ids: list[str] = Field(
        default_factory=list,
        description="包含的业务 Job ID 列表（来自 AnalysisResult.pipelines[].jobs）",
    )

    # 执行阶段（由 ArchitectAgent 规划）
    stages: list[Stage] = Field(default_factory=list, description="执行阶段列表")

    # 数据读写声明（通过共享存储传递数据）
    input_tables: list[str] = Field(default_factory=list, description="读取的表列表")
    output_table: str | None = Field(None, description="写入的目标表")

    # 组件配置（由 DeveloperAgent 生成）
    config: dict[str, Any] = Field(default_factory=dict, description="组件配置")

    # 运行时配置
    priority: int = Field(default=0, description="优先级")
    timeout: int = Field(default=3600, description="超时时间（秒）")
    retry_times: int = Field(default=3, description="失败重试次数")
    retry_interval: int = Field(default=60, description="重试间隔（秒）")

    # 状态标记
    config_generated: bool = Field(default=False, description="配置是否已生成")
    config_validated: bool = Field(default=False, description="配置是否已验证")

    def get_ordered_stages(self) -> list[Stage]:
        """按执行顺序返回 Stage"""
        return sorted(self.stages, key=lambda s: s.stage_id)


class Workflow(BaseModel):
    """
    工作流定义（统一三端命名：Workflow）

    描述完整的 ETL 工作流，由多个 Job 组成的 DAG。
    由 ArchitectAgent 根据业务分析结果规划。
    """

    # 基础信息
    id: str | None = Field(None, description="工作流唯一标识")
    name: str = Field(..., description="工作流名称")
    description: str | None = Field(None, description="工作流描述")

    # 调度配置
    schedule: str | None = Field(None, description="调度周期 cron 表达式")
    env: Literal["dev", "stg", "prod"] = Field(default="dev", description="运行环境")

    # 作业列表
    jobs: list[Job] = Field(default_factory=list, description="作业列表")

    # 风险提示
    risks: list[str] = Field(default_factory=list, description="架构风险点")

    # 决策点（需要用户确认）
    decision_points: list[dict[str, Any]] = Field(default_factory=list)

    # 置信度
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    @classmethod
    def from_output(
        cls,
        output: "WorkflowOutput",
        selected_component: str,
        selected_component_id: int | None = None,
    ) -> "Workflow":
        """
        从 LLM 输出构建完整的 Workflow

        Args:
            output: LLM 生成的 WorkflowOutput
            selected_component: 用户选择的组件类型
            selected_component_id: 组件 ID
        """
        jobs = []
        for job_output in output.jobs:
            stages = [
                Stage(
                    stage_id=s.stage_id,
                    name=s.name,
                    description=s.description,
                    input_tables=s.input_tables,
                    output_table=s.output_table,
                    is_temp_table=s.is_temp_table,
                    sql=s.sql,
                )
                for s in job_output.stages
            ]
            job = Job(
                id=job_output.id,
                name=job_output.name,
                description=job_output.description,
                type=selected_component,
                type_id=selected_component_id,
                depends=job_output.depends,
                job_ids=job_output.job_ids,
                stages=stages,
                input_tables=job_output.input_tables,
                output_table=job_output.output_table,
            )
            jobs.append(job)

        return cls(
            name=output.name,
            description=output.description,
            schedule=output.schedule,
            jobs=jobs,
            risks=output.risks,
            confidence=output.confidence,
        )

    def get_job(self, job_id: str) -> Job | None:
        """获取作业"""
        for job in self.jobs:
            if job.id == job_id:
                return job
        return None

    def get_upstream_jobs(self, job_id: str) -> list[Job]:
        """获取上游作业"""
        job = self.get_job(job_id)
        if not job:
            return []
        return [j for j in self.jobs if j.id in job.depends]

    def get_downstream_jobs(self, job_id: str) -> list[Job]:
        """获取下游作业"""
        return [j for j in self.jobs if job_id in j.depends]

    def get_root_jobs(self) -> list[Job]:
        """获取根作业（无依赖的作业）"""
        return [j for j in self.jobs if not j.depends]

    def get_leaf_jobs(self) -> list[Job]:
        """获取叶子作业（无下游的作业）"""
        all_deps = set()
        for job in self.jobs:
            all_deps.update(job.depends)
        return [j for j in self.jobs if j.id not in all_deps]

    def topological_sort(self) -> list[Job]:
        """拓扑排序（按执行顺序）"""
        visited = set()
        result = []

        def dfs(job_id: str):
            if job_id in visited:
                return
            visited.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep_id in job.depends:
                    dfs(dep_id)
                result.append(job)

        for job in self.jobs:
            dfs(job.id)

        return result

    def topological_layers(self) -> list[list[Job]]:
        """
        拓扑分层（按依赖层级分组）

        返回按执行顺序排列的层列表，同一层内的 Job 可以并行执行。
        例如：[[job_1, job_2], [job_3], [job_4, job_5]]
        表示 job_1 和 job_2 可以并行，完成后 job_3 执行，最后 job_4 和 job_5 并行。

        算法：Kahn's algorithm（BFS 拓扑排序）
        """
        if not self.jobs:
            return []

        # 构建入度表和邻接表
        in_degree: dict[str, int] = {job.id: 0 for job in self.jobs}
        adjacency: dict[str, list[str]] = {job.id: [] for job in self.jobs}

        for job in self.jobs:
            for dep_id in job.depends:
                if dep_id in adjacency:
                    adjacency[dep_id].append(job.id)
                    in_degree[job.id] += 1

        layers: list[list[Job]] = []

        # BFS 分层
        while True:
            # 找出当前入度为 0 的所有节点（当前层）
            current_layer_ids = [job_id for job_id, degree in in_degree.items() if degree == 0]
            if not current_layer_ids:
                break

            # 获取当前层的 Job 对象
            current_layer = [job for job in self.jobs if job.id in current_layer_ids]
            if current_layer:
                layers.append(current_layer)

            # 移除当前层节点，更新下游节点的入度
            for job_id in current_layer_ids:
                del in_degree[job_id]
                for downstream_id in adjacency.get(job_id, []):
                    if downstream_id in in_degree:
                        in_degree[downstream_id] -= 1

        return layers

    def validate_dag(self) -> list[str]:
        """验证 DAG 是否合法"""
        errors = []

        # 检查 Job ID 唯一性
        ids = [j.id for j in self.jobs]
        if len(ids) != len(set(ids)):
            errors.append("存在重复的 Job ID")

        # 检查依赖是否存在
        id_set = set(ids)
        for job in self.jobs:
            for dep in job.depends:
                if dep not in id_set:
                    errors.append(f"Job {job.id} 依赖的 {dep} 不存在")

        # 检查循环依赖
        def has_cycle(job_id: str, path: set) -> bool:
            if job_id in path:
                return True
            path.add(job_id)
            job = self.get_job(job_id)
            if job:
                for dep in job.depends:
                    if has_cycle(dep, path.copy()):
                        return True
            return False

        for job in self.jobs:
            if has_cycle(job.id, set()):
                errors.append(f"存在循环依赖，涉及 Job {job.id}")
                break

        return errors

    def validate_data_dependencies(self) -> tuple[list[str], list[str]]:
        """
        验证数据依赖是否正确声明

        检查逻辑：
        - 如果 Job B 的 input_tables 包含 Job A 的 output_table
        - 则 Job B 必须声明依赖 Job A（Job B.depends 包含 Job A.id）

        Returns:
            (errors, warnings): 错误列表和警告列表
            - errors: 缺失的关键依赖（会导致调度失败）
            - warnings: 可能的问题（需要人工确认）
        """
        errors = []
        warnings = []

        # 构建 output_table -> job_id 映射
        output_to_job: dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # 检查每个 Job 的 input_tables
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                # 检查这个输入表是否由其他 Job 产出
                producer_job_id = output_to_job.get(input_table)
                if (
                    producer_job_id
                    and producer_job_id != job.id
                    and producer_job_id not in job.depends
                ):
                    errors.append(
                        f"Job '{job.id}' 读取表 '{input_table}'，"
                        f"该表由 Job '{producer_job_id}' 产出，"
                        f"但未声明依赖关系"
                    )

        return errors, warnings

    def fix_missing_dependencies(self) -> list[str]:
        """
        自动修复缺失的数据依赖

        Returns:
            修复记录列表
        """
        fixes = []

        # 构建 output_table -> job_id 映射
        output_to_job: dict[str, str] = {}
        for job in self.jobs:
            if job.output_table:
                output_to_job[job.output_table] = job.id

        # 检查并修复每个 Job
        for job in self.jobs:
            if not job.input_tables:
                continue

            for input_table in job.input_tables:
                producer_job_id = output_to_job.get(input_table)
                if (
                    producer_job_id
                    and producer_job_id != job.id
                    and producer_job_id not in job.depends
                ):
                    job.depends.append(producer_job_id)
                    fixes.append(
                        f"自动添加依赖: Job '{job.id}' -> Job '{producer_job_id}' "
                        f"(因为读取表 '{input_table}')"
                    )

        return fixes

    def validate_temp_scope(self) -> list[str]:
        """
        验证临时表作用域

        规则：Stage 产出的临时表（is_temp_table=True）只能在当前 Job 内部使用，
        不能被其他 Job 引用。

        Returns:
            错误列表
        """
        errors = []

        # 收集每个 Job 内部的临时表
        job_temp_tables: dict[str, set] = {}  # job_id -> {temp_table1, temp_table2}

        for job in self.jobs:
            temp_tables = set()
            for stage in job.stages:
                if stage.is_temp_table and stage.output_table:
                    temp_tables.add(stage.output_table)
            job_temp_tables[job.id] = temp_tables

        # 合并所有临时表
        all_temp_tables: dict[str, str] = {}  # temp_table -> owner_job_id
        for job_id, temp_tables in job_temp_tables.items():
            for temp_table in temp_tables:
                all_temp_tables[temp_table] = job_id

        # 检查每个 Job 是否引用了其他 Job 的临时表
        for job in self.jobs:
            # 检查 Job 级别的 input_tables
            for input_table in job.input_tables:
                if input_table in all_temp_tables:
                    owner_job_id = all_temp_tables[input_table]
                    if owner_job_id != job.id:
                        errors.append(
                            f"Job '{job.id}' 引用了 Job '{owner_job_id}' 的临时表 '{input_table}'，"
                            f"临时表只能在定义它的 Job 内部使用"
                        )

            # 检查 Stage 级别的 input_tables
            for stage in job.stages:
                for input_table in stage.input_tables:
                    if input_table in all_temp_tables:
                        owner_job_id = all_temp_tables[input_table]
                        if owner_job_id != job.id:
                            errors.append(
                                f"Job '{job.id}' 的 Stage '{stage.name}' 引用了 "
                                f"Job '{owner_job_id}' 的临时表 '{input_table}'，"
                                f"临时表只能在定义它的 Job 内部使用"
                            )

        return errors
