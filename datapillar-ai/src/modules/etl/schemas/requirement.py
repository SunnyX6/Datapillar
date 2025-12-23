"""
需求分析数据结构

AnalystAgent 的职责是业务分析：
- 将用户需求拆分为业务步骤（Step）
- 只关心"做什么"，不关心"怎么做"
- 不涉及 SQL、Stage 等技术细节
"""

from typing import List, Optional
from pydantic import BaseModel, Field


class Step(BaseModel):
    """
    Step - 业务步骤

    业务层面的概念，表示完成需求的一个逻辑步骤。
    不涉及 SQL、Stage 等技术实现细节。
    """
    step_id: str = Field(..., description="步骤唯一标识")
    step_name: str = Field(..., description="步骤名称")
    description: str = Field(..., description="这一步做什么（业务描述）")

    input_tables: List[str] = Field(default_factory=list, description="需要读取的表")
    output_table: Optional[str] = Field(None, description="产出的表")

    depends_on: List[str] = Field(default_factory=list, description="依赖的上游 Step ID")


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

    业务层面的分析：
    - 将用户需求拆分为业务步骤（Step）
    - 识别数据目标
    - 标注歧义点

    不涉及技术实现（Job、Stage、SQL），那是架构师的职责。
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
            tables.update(step.input_tables)
            if step.output_table:
                tables.add(step.output_table)
        if self.final_target:
            tables.add(self.final_target.table_name)
        return list(tables)

    def get_step_by_id(self, step_id: str) -> Optional[Step]:
        """根据 ID 获取 Step"""
        for step in self.steps:
            if step.step_id == step_id:
                return step
        return None

    def get_execution_plan_summary(self) -> str:
        """获取执行计划摘要"""
        lines = []
        for step in self.steps:
            deps = f" (依赖: {', '.join(step.depends_on)})" if step.depends_on else ""
            output = f" → {step.output_table}" if step.output_table else ""
            lines.append(f"[{step.step_name}]{deps}{output}")
            lines.append(f"  {step.description}")
        return "\n".join(lines)
