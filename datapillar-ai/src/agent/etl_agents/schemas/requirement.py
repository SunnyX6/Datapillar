"""
需求分析数据结构

核心理念：从业务角度拆分需求，分而治之
- Step：业务步骤，完成需求的一个逻辑步骤
- Stage：SQL 任务，实现业务步骤的具体 SQL

拆分原则：用户需求 → 拆成几个业务步骤 → 每个步骤一个 Step
"""

from typing import List, Optional
from pydantic import BaseModel, Field


class Stage(BaseModel):
    """
    Stage - SQL 任务

    一个 Stage 对应一个 SQL，完成业务步骤中的具体工作。
    一个 Step 可以包含多个 Stage。
    """
    stage_id: int = Field(..., description="Stage 序号（Step 内唯一）")
    name: str = Field(..., description="Stage 名称")
    description: str = Field(..., description="这个 SQL 做什么")

    input_tables: List[str] = Field(default_factory=list, description="读取的表")
    output_table: str = Field(..., description="输出表")
    is_temp_table: bool = Field(default=True, description="是否是临时表")

    sql: Optional[str] = Field(None, description="SQL 语句（由 DeveloperAgent 生成）")


class Step(BaseModel):
    """
    Step - 业务步骤

    完成需求的一个逻辑步骤，对应前端的一个节点。
    一个 Step 可以包含多个 Stage（多个 SQL）。
    """
    step_id: str = Field(..., description="步骤唯一标识")
    step_name: str = Field(..., description="步骤名称")
    description: Optional[str] = Field(None, description="这一步做什么")

    stages: List[Stage] = Field(default_factory=list, description="该步骤包含的 Stage 列表")

    depends_on: List[str] = Field(default_factory=list, description="依赖的上游 Step ID")
    output_table: Optional[str] = Field(None, description="该步骤的输出表")

    suggested_component: str = Field(default="hive", description="建议的组件")

    def get_all_input_tables(self) -> List[str]:
        """获取所有外部输入表（排除内部临时表）"""
        internal_tables = {s.output_table for s in self.stages if s.is_temp_table}
        all_inputs = set()
        for stage in self.stages:
            for t in stage.input_tables:
                if t not in internal_tables:
                    all_inputs.add(t)
        return list(all_inputs)

    def get_ordered_stages(self) -> List[Stage]:
        """按执行顺序返回 Stage"""
        return sorted(self.stages, key=lambda s: s.stage_id)


class DataTarget(BaseModel):
    """最终数据目标"""
    table_name: str
    write_mode: str = Field(default="overwrite", description="写入模式：overwrite/append/upsert")
    partition_by: List[str] = Field(default_factory=list, description="分区字段")
    description: Optional[str] = None


class Ambiguity(BaseModel):
    """歧义点"""
    question: str = Field(..., description="需要澄清的问题")
    context: Optional[str] = Field(None, description="为什么需要澄清")
    options: List[str] = Field(default_factory=list, description="可能的选项")


class AnalysisResult(BaseModel):
    """
    需求分析结果（AnalystAgent 输出）

    核心理念：从业务角度拆分需求
    - 用户需求 → 拆成几个业务步骤 → 每个步骤一个 Step
    - 每个 Step 可以包含多个 Stage（SQL）
    """

    user_query: str = Field(..., description="用户原始输入")
    summary: str = Field(..., description="一句话概括用户需求")

    steps: List[Step] = Field(default_factory=list, description="业务步骤列表")

    final_target: Optional[DataTarget] = Field(None, description="最终数据目标")
    ambiguities: List[Ambiguity] = Field(default_factory=list, description="需要澄清的歧义点")
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    def needs_clarification(self) -> bool:
        """是否需要用户澄清"""
        return len(self.ambiguities) > 0

    def get_all_tables(self) -> List[str]:
        """获取所有涉及的表"""
        tables = set()
        for step in self.steps:
            for stage in step.stages:
                tables.update(stage.input_tables)
                tables.add(stage.output_table)
        if self.final_target:
            tables.add(self.final_target.table_name)
        return list(tables)

    def get_step_dependency_order(self) -> List[Step]:
        """按依赖顺序返回 Step（拓扑排序）"""
        step_map = {s.step_id: s for s in self.steps}
        visited = set()
        result = []

        def dfs(step_id: str):
            if step_id in visited:
                return
            visited.add(step_id)
            step = step_map.get(step_id)
            if step:
                for dep_id in step.depends:
                    dfs(dep_id)
                result.append(step)

        for step in self.steps:
            dfs(step.step_id)

        return result

    def get_execution_plan_summary(self) -> str:
        """获取执行计划摘要"""
        lines = []
        for step in self.get_step_dependency_order():
            deps = f" (依赖: {', '.join(step.depends)})" if step.depends else ""
            lines.append(f"[{step.step_name}]{deps}")
            for stage in step.get_ordered_stages():
                lines.append(f"  └─ {stage.name} → {stage.output_table}")
        return "\n".join(lines)
